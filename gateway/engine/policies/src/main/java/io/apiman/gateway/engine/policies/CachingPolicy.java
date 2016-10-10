/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apiman.gateway.engine.policies;

import io.apiman.gateway.engine.beans.ApiRequest;
import io.apiman.gateway.engine.beans.ApiResponse;
import io.apiman.gateway.engine.beans.exceptions.ComponentNotFoundException;
import io.apiman.gateway.engine.components.ICacheStoreComponent;
import io.apiman.gateway.engine.impl.CachedResponse;
import io.apiman.gateway.engine.io.AbstractStream;
import io.apiman.gateway.engine.io.IApimanBuffer;
import io.apiman.gateway.engine.io.IReadWriteStream;
import io.apiman.gateway.engine.io.ISignalReadStream;
import io.apiman.gateway.engine.io.ISignalWriteStream;
import io.apiman.gateway.engine.policies.caching.CacheConnectorInterceptor;
import io.apiman.gateway.engine.policies.caching.MIMEParse;
import io.apiman.gateway.engine.policies.config.CachingConfig;
import io.apiman.gateway.engine.policy.IConnectorInterceptor;
import io.apiman.gateway.engine.policy.IDataPolicy;
import io.apiman.gateway.engine.policy.IPolicyChain;
import io.apiman.gateway.engine.policy.IPolicyContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;

import static io.apiman.gateway.engine.policies.caching.MIMEParse.parseMediaRange;
import static java.util.Optional.ofNullable;

/**
 * Policy that enables caching for back-end APIs responses.
 *
 * @author rubenrm1@gmail.com
 */
public class CachingPolicy extends AbstractMappedDataPolicy<CachingConfig> implements IDataPolicy {

    private static final String KEY_SEPARATOR = ":"; //$NON-NLS-1$
    private static final String SHOULD_CACHE_ATTR = CachingPolicy.class.getName() + ".should-cache"; //$NON-NLS-1$
    private static final String CACHE_ID_ATTR = CachingPolicy.class.getName() + ".cache-id"; //$NON-NLS-1$
    private static final String CACHED_RESPONSE = CachingPolicy.class.getName() + ".cached-response"; //$NON-NLS-1$

    /**
     * Constructor.
     */
    public CachingPolicy() {
    }

    /**
     * @see io.apiman.gateway.engine.policy.AbstractPolicy#getConfigurationClass()
     */
    @Override
    protected Class<CachingConfig> getConfigurationClass() {
        return CachingConfig.class;
    }

    /**
     * If the request is cached an {@link IConnectorInterceptor} is set in order to prevent the back-end connection to be established.
     * Otherwise an empty {@link CachedResponse} will be added to the context, this will be used to cache the response once it has been
     * received from the back-end API
     *
     * @see io.apiman.gateway.engine.policies.AbstractMappedPolicy#doApply(io.apiman.gateway.engine.beans.ApiRequest, io.apiman.gateway.engine.policy.IPolicyContext, java.lang.Object, io.apiman.gateway.engine.policy.IPolicyChain)
     */
    @Override
    protected void doApply(final ApiRequest request, final IPolicyContext context, final CachingConfig config,
            final IPolicyChain<ApiRequest> chain) {
        if (config.getTtl() > 0) {
            // Check to see if there is a cache entry for this request.  If so, we need to
            // short-circuit the connector factory by providing a connector interceptor
            final String cacheId = buildCacheID(request);
            context.setAttribute(CACHE_ID_ATTR, cacheId);

            final ICacheStoreComponent cache = context.getComponent(ICacheStoreComponent.class);

            final String idSuffix = determineHighestContentType(request)
                    .map(this::generateContentTypeSuffix).orElse("");

            if (StringUtils.isBlank(idSuffix)) {
                // no explicit content type requested - use default, if present
                lookupDefault(request, context, chain, cache, cacheId);

            } else {
                // lookup using requested content type
                lookupWithSuffix(request, context, chain, cache, cacheId, idSuffix);
            }

        } else {
            context.setAttribute(SHOULD_CACHE_ATTR, Boolean.FALSE);
            chain.doApply(request);
        }
    }

    /**
     * Determine the requested content type from the 'Accept' header with the highest 'q' value.
     *
     * @param request
     * @return the most desired content type, or {@link Optional#empty()}
     */
    private Optional<String> determineHighestContentType(ApiRequest request) {
        final String acceptHeader = request.getHeaders().get("Accept");
        if (StringUtils.isBlank(acceptHeader)) {
            return Optional.empty();

        } else {
            final List<MIMEParse.ParseResults> results = new LinkedList<>();
            for (String r : StringUtils.split(acceptHeader, ',')) {
                results.add(parseMediaRange(r));
            }

            if (results.size() == 0) {
                return Optional.empty();

            } else {
                // determine the highest ranked
                Collections.sort(results, (o1, o2) -> o1.params.get("q").compareTo(o2.params.get("q")));
                final MIMEParse.ParseResults highest = results.get(results.size() - 1);

                return Optional.of(highest.type + "/" + highest.subType);
            }
        }
    }

    /**
     * Look for a cached value using the cache ID and content type suffix, falling back to
     * {@link #lookupDefault(ApiRequest, IPolicyContext, IPolicyChain, ICacheStoreComponent, String)}, if not found.
     *
     * @param request
     * @param context
     * @param chain
     * @param cache
     * @param cacheId
     * @param contentTypeSuffix
     */
    private void lookupWithSuffix(final ApiRequest request, final IPolicyContext context, final IPolicyChain<ApiRequest> chain,
                                  final ICacheStoreComponent cache, final String cacheId, final String contentTypeSuffix) {

        cache.getBinary(cacheId + contentTypeSuffix, ApiResponse.class, result -> {
            if (result.isError()) {
                chain.throwError(result.getError());
            } else {
                final ISignalReadStream<ApiResponse> cacheEntry = result.getResult();
                if (null == cacheEntry) {
                    // fall back to default, if present
                    lookupDefault(request, context, chain, cache, cacheId);

                } else {
                    prepareCachedResponse(cacheEntry, context);
                    chain.doApply(request);
                }
            }
        });
    }

    /**
     * Look for a cached value using the default cache ID, which ignores content type.
     *  @param request
     * @param context
     * @param chain
     * @param cache
     * @param cacheId
     */
    private void lookupDefault(final ApiRequest request, final IPolicyContext context, final IPolicyChain<ApiRequest> chain,
                               final ICacheStoreComponent cache, final String cacheId) {

        cache.getBinary(cacheId, ApiResponse.class, result -> {
                    if (result.isError()) {
                        chain.throwError(result.getError());
                    } else {
                        final ISignalReadStream<ApiResponse> cacheEntry = result.getResult();
                        if (null != cacheEntry) {
                            prepareCachedResponse(cacheEntry, context);
                        }
                        chain.doApply(request);
                    }
                });
    }

    /**
     * Prepare a response using the provided {@code cacheEntry}.
     *
     * @param cacheEntry
     * @param context
     */
    private void prepareCachedResponse(final ISignalReadStream<ApiResponse> cacheEntry, final IPolicyContext context) {
        context.setConnectorInterceptor(new CacheConnectorInterceptor(cacheEntry));
        context.setAttribute(SHOULD_CACHE_ATTR, Boolean.FALSE);
        context.setAttribute(CACHED_RESPONSE, cacheEntry.getHead());
    }

    /**
     * @see AbstractMappedPolicy#doApply(ApiResponse, IPolicyContext, Object, IPolicyChain)
     */
    @Override
    protected void doApply(ApiResponse response, IPolicyContext context, CachingConfig config,
            IPolicyChain<ApiResponse> chain) {

        if (context.getAttribute(SHOULD_CACHE_ATTR, Boolean.TRUE)) {
            if (response.getCode() == HttpURLConnection.HTTP_OK) {
                // add content type suffix
                ofNullable(response.getHeaders().get("Content-Type"))
                        .filter(StringUtils::isNotBlank)
                        .ifPresent(contentType -> {
                            final String cacheId = context.getAttribute(CACHE_ID_ATTR, null);
                            context.setAttribute(CACHE_ID_ATTR, cacheId + generateContentTypeSuffix(contentType));
                        });

            } else {
                // don't cache non-200 responses
                context.setAttribute(SHOULD_CACHE_ATTR, Boolean.FALSE);
            }
        }

        chain.doApply(response);
    }

    /**
     * @see io.apiman.gateway.engine.policies.AbstractMappedDataPolicy#requestDataHandler(io.apiman.gateway.engine.beans.ApiRequest, io.apiman.gateway.engine.policy.IPolicyContext, java.lang.Object)
     */
    @Override
    protected IReadWriteStream<ApiRequest> requestDataHandler(ApiRequest request,
            IPolicyContext context, CachingConfig policyConfiguration) {
        // No need to handle the request stream (e.g. POST body)
        return null;
    }

    /**
     * @see io.apiman.gateway.engine.policies.AbstractMappedDataPolicy#responseDataHandler(io.apiman.gateway.engine.beans.ApiResponse, io.apiman.gateway.engine.policy.IPolicyContext, java.lang.Object)
     */
    @Override
    protected IReadWriteStream<ApiResponse> responseDataHandler(final ApiResponse response,
            IPolicyContext context, CachingConfig policyConfiguration) {
        // Possible cache the response for future posterity.
        if (context.getAttribute(SHOULD_CACHE_ATTR, Boolean.TRUE)) {
            try {
                final String cacheId = context.getAttribute(CACHE_ID_ATTR, null);

                final ICacheStoreComponent cache = context.getComponent(ICacheStoreComponent.class);
                final ISignalWriteStream writeStream = cache.putBinary(cacheId, response, policyConfiguration.getTtl());
                return new AbstractStream<ApiResponse>() {
                    @Override
                    public ApiResponse getHead() {
                        return response;
                    }
                    @Override
                    protected void handleHead(ApiResponse head) {
                    }
                    @Override
                    public void write(IApimanBuffer chunk) {
                        writeStream.write(chunk);
                        super.write(chunk);
                    }
                    @Override
                    public void end() {
                        writeStream.end();
                        super.end();
                    }
                };
            } catch (ComponentNotFoundException | IOException e) {
                // TODO log error
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Builds a cached request id composed by the API key followed by the HTTP
     * verb and the destination. In the case where there's no API key the ID
     * will contain ApiOrgId + ApiId + ApiVersion
     */
    private static String buildCacheID(ApiRequest request) {
        StringBuilder req = new StringBuilder();
        if (request.getContract() != null) {
            req.append(request.getApiKey());
        } else {
            req.append(request.getApiOrgId()).append(KEY_SEPARATOR).append(request.getApiId())
                    .append(KEY_SEPARATOR).append(request.getApiVersion());
        }
        req.append(KEY_SEPARATOR).append(request.getType()).append(KEY_SEPARATOR)
                .append(request.getDestination());

        return req.toString();
    }

    /**
     * Generates a suffix for the cache ID, based on the given {@code contentType}.
     *
     * Note: the type is normalised to lowercase first, as
     * 'The type, subtype, and parameter names are not case sensitive.', as per
     * http://www.w3.org/Protocols/rfc1341/4_Content-Type.html
     *
     * @param contentType the Content Type
     * @return the cache ID suffix
     */
    private String generateContentTypeSuffix(String contentType) {
        return KEY_SEPARATOR + Base64.encodeBase64String(contentType.toLowerCase().getBytes());
    }
}
