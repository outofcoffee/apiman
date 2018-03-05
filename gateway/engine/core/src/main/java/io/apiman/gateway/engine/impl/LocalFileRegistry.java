package io.apiman.gateway.engine.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiman.gateway.engine.async.IAsyncResultHandler;
import io.apiman.gateway.engine.beans.Api;
import io.apiman.gateway.engine.beans.Client;
import io.apiman.gateway.engine.beans.exceptions.RegistrationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author pcornish
 */
public class LocalFileRegistry extends InMemoryRegistry {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Object mutex = new Object();
    private final File registryFile;

    /**
     * The cached version of the registry map.
     */
    private Map<String, Object> map;

    public LocalFileRegistry(Map<String, String> config) {
        final String registryPath = config.get("registry.path");
        if (StringUtils.isEmpty(registryPath)) {
            throw new IllegalStateException("Registry path is not set");
        } else {
            registryFile = new File(registryPath);
        }
    }

    /**
     * Read the {@link #map} from the filesystem.
     *
     * @return the registry map
     */
    @SuppressWarnings("unchecked")
    @Override
    protected Map<String, Object> getMap() {
        if (null == map) {
            synchronized (mutex) {
                // double-guard
                if (null == map) {
                    try (final InputStream in = FileUtils.openInputStream(registryFile)) {
                        map = (Map<String, Object>) mapper.readValue(in, Map.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Error reading registry from file: " + registryFile, e);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Store the {@link #map} to the filesystem.
     */
    private void persistMap() {
        synchronized (mutex) {
            try (final OutputStream out = FileUtils.openOutputStream(registryFile)) {
                mapper.writeValue(out, map);
            } catch (Exception e) {
                throw new RuntimeException("Error persisting registry to file: " + registryFile, e);
            }
        }
    }

    @Override
    public void publishApi(Api api, IAsyncResultHandler<Void> handler) {
        super.publishApi(api, handler);
        persistMap();
    }

    @Override
    public void retireApi(Api api, IAsyncResultHandler<Void> handler) {
        super.retireApi(api, handler);
        persistMap();
    }

    @Override
    public void registerClient(Client client, IAsyncResultHandler<Void> handler) {
        super.registerClient(client, handler);
        persistMap();
    }

    @Override
    public void unregisterClient(Client client, IAsyncResultHandler<Void> handler) {
        super.unregisterClient(client, handler);
        persistMap();
    }

    @Override
    protected void unregisterClientInternal(Client client, boolean silent) throws RegistrationException {
        super.unregisterClientInternal(client, silent);
        persistMap();
    }
}
