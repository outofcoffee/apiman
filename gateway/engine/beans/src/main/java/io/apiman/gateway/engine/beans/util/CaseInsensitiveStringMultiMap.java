/*
 * Copyright 2015 JBoss Inc
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

package io.apiman.gateway.engine.beans.util;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * A simple multimap able to accept multiple values for a given key.
 * <p>
 * The implementation is specifically tuned for headers (such as HTTP), where
 * the number of entries tends to be moderate, but are frequently accessed.
 * </p>
 * <p>
 * This map expects ASCII for key strings.
 * </p>
 * <p>
 * Case is ignored (avoiding {@link String#toLowerCase()} <code>String</code> allocation)
 * before being hashed (<code>xxHash</code>).
 * </p>
 * <p>
 * Constraints:
 * <ul>
 * <li><strong>Not thread-safe.</strong></li>
 * <li><tt>Null</tt> is <strong>not</strong> a valid key.</li>
 * </ul>
 * </p>
 *
 * @author Marc Savy {@literal <msavy@redhat.com>}
 */
public class CaseInsensitiveStringMultiMap implements IStringMultiMap, Serializable {
    private static final long serialVersionUID = -2052530527825235543L;

    private Map<String, List<AbstractMap.SimpleImmutableEntry<String, String>>> store;

    @SuppressWarnings("WeakerAccess")
    public CaseInsensitiveStringMultiMap() {
        store = new LinkedHashMap<>();
    }

    @SuppressWarnings("WeakerAccess")
    public CaseInsensitiveStringMultiMap(int sizeHint) {
        store = new LinkedHashMap<>(sizeHint);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return getEntries().iterator();
    }

    @SuppressWarnings("unchecked")
    private void putMultiValue(String key, String value, boolean replaceAll) {
        final String normalisedKey = key.toLowerCase();

        List<AbstractMap.SimpleImmutableEntry<String, String>> entries = null;

        if (!replaceAll) {
            entries = store.get(normalisedKey);
        }
        if (null == entries) {
            entries = new ArrayList<>();
            store.put(normalisedKey, entries);
        }

        entries.add(new AbstractMap.SimpleImmutableEntry(key, value));
    }

    @Override
    public IStringMultiMap put(String key, String value) {
        putMultiValue(key, value, true);
        return this;
    }

    @Override
    public IStringMultiMap putAll(Map<String, String> map) {
        //noinspection SimplifyStreamApiCallChains
        map.entrySet().stream().forEachOrdered(pair -> put(pair.getKey(), pair.getValue()));
        return this;
    }

    @Override
    public IStringMultiMap add(String key, String value) {
        putMultiValue(key, value, false);
        return this;
    }

    @Override
    public IStringMultiMap addAll(Map<String, String> map) {
        //noinspection SimplifyStreamApiCallChains
        map.entrySet().stream().forEachOrdered(pair -> put(pair.getKey(), pair.getValue()));
        return this;
    }

    @Override
    public IStringMultiMap addAll(IStringMultiMap map) {
        //noinspection SimplifyStreamApiCallChains
        map.getEntries().stream().forEachOrdered(pair -> add(pair.getKey(), pair.getValue()));
        return this;
    }

    @Override
    public IStringMultiMap remove(String key) {
        // normalise
        key = key.toLowerCase();

        store.remove(key);
        return this;
    }

    @Override
    public String get(String key) {
        // normalise
        key = key.toLowerCase();

        final List<AbstractMap.SimpleImmutableEntry<String, String>> entries = store.get(key);
        return ofNullable(entries).orElse(emptyList()).stream()
                .map(AbstractMap.SimpleImmutableEntry::getValue)
                .findFirst().orElse(null);
    }

    @Override
    public List<Entry<String, String>> getAllEntries(String key) {
        // normalise
        key = key.toLowerCase();

        return ofNullable(store.get(key)).orElse(emptyList()).stream()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAll(String key) {
        // normalise
        key = key.toLowerCase();

        return ofNullable(store.get(key)).orElse(emptyList()).stream()
                .map(AbstractMap.SimpleImmutableEntry::getValue)
                .collect(Collectors.toList());
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public List<Entry<String, String>> getEntries() {
        return store.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * As per the interface contract in {@link IStringMultiMap#toMap()}, only the first
     * value should be returned for a key.
     *
     * @return the map, keyed by the entry's original key, not the normalised key in the store
     */
    @Override
    public Map<String, String> toMap() {
        final Map<String, String> map = new HashMap<>();

        store.forEach((key, entries) -> {
            if (entries.size() > 0) {
                map.put(entries.get(0).getKey(), entries.get(0).getValue());
            }
        });

        return map;
    }

    @Override
    public boolean containsKey(String key) {
        // normalise
        key = key.toLowerCase();

        return store.containsKey(key);
    }

    @Override
    public Set<String> keySet() {
        return store.keySet();
    }

    @Override
    public IStringMultiMap clear() {
        store.clear();
        return this;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        store.forEach((key, entries) -> {
            sb.append(sb.length() > 0 ? ", " : "").append(key).append(" => [");

            final List<AbstractMap.SimpleImmutableEntry<String, String>> values =
                    ofNullable(entries).orElse(emptyList());

            for (int i = 0; i < values.size(); i++) {
                sb.append(i > 0 ? ", " : "").append(values.get(i).getValue());
            }

            sb.append("]");
        });

        return "{" + sb.toString() + "}";
    }
}
