package io.apiman.gateway.engine.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiman.gateway.engine.async.IAsyncResultHandler;
import io.apiman.gateway.engine.beans.Api;
import io.apiman.gateway.engine.beans.Client;
import io.apiman.gateway.engine.beans.exceptions.RegistrationException;
import io.apiman.gateway.engine.filesystem.model.RegistryWrapper;
import io.apiman.gateway.engine.impl.InMemoryRegistry;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adds local file based registry implementation.
 *
 * @author Pete Cornish
 */
public class LocalFileRegistry extends InMemoryRegistry {
    static final String CONFIG_REGISTRY_PATH = "registry.path";

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileRegistry.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Object mutex = new Object();
    private final File registryFile;

    /**
     * The cached version of the registry.
     */
    private Map<String, Object> map;

    public LocalFileRegistry(Map<String, String> config) {
        final String registryPath = config.get(CONFIG_REGISTRY_PATH);
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
                    final Map<String, Object> registryMap = new ConcurrentHashMap<>();
                    if (registryFile.exists()) {
                        try (final InputStream in = FileUtils.openInputStream(registryFile)) {
                            final RegistryWrapper wrapper = mapper.readValue(in, RegistryWrapper.class);

                            registryMap.putAll(wrapper.getApis().stream()
                                .collect(Collectors.toMap(this::getApiIndex, Function.identity())));
                            registryMap.putAll(wrapper.getClients().stream()
                                .collect(Collectors.toMap(this::getClientIndex, Function.identity())));

                        } catch (Exception e) {
                            throw new RuntimeException("Error reading registry from file: " + registryFile, e);
                        }
                    } else {
                        LOGGER.debug("Registry file '{}' does not exist - starting with an empty registry", registryFile);
                    }
                    map = registryMap;
                }
            }
        }
        return map;
    }

    /**
     * Store the {@link #map} to the filesystem.
     */
    private void persist() {
        synchronized (mutex) {
            try (final OutputStream out = FileUtils.openOutputStream(registryFile)) {
                final RegistryWrapper wrapper = new RegistryWrapper();
                wrapper.getApis().addAll(filterByType(map, Api.class));
                wrapper.getClients().addAll(filterByType(map, Client.class));

                mapper.writeValue(out, wrapper);

            } catch (Exception e) {
                throw new RuntimeException("Error persisting registry to file: " + registryFile, e);
            }
        }
    }

    void clear() {
        synchronized (mutex) {
            map = null;
        }
    }

    @SuppressWarnings("unchecked")
    private <V> Collection<V> filterByType(Map<String, ?> entries, Class<V> clazz) {
        return entries.values().stream()
                .filter(e -> clazz.isAssignableFrom(e.getClass()))
                .map(clazz::cast)
                .collect(Collectors.toSet());
    }

    @Override
    public void publishApi(Api api, IAsyncResultHandler<Void> handler) {
        super.publishApi(api, handler);
        persist();
    }

    @Override
    public void retireApi(Api api, IAsyncResultHandler<Void> handler) {
        super.retireApi(api, handler);
        persist();
    }

    @Override
    public void registerClient(Client client, IAsyncResultHandler<Void> handler) {
        super.registerClient(client, handler);
        persist();
    }

    @Override
    public void unregisterClient(Client client, IAsyncResultHandler<Void> handler) {
        super.unregisterClient(client, handler);
        persist();
    }

    @Override
    protected void unregisterClientInternal(Client client, boolean silent) throws RegistrationException {
        super.unregisterClientInternal(client, silent);
        persist();
    }
}
