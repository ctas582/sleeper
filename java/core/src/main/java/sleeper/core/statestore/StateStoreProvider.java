/*
 * Copyright 2022-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sleeper.core.statestore;

import sleeper.core.properties.instance.InstanceProperties;
import sleeper.core.properties.table.TableProperties;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import static sleeper.core.properties.instance.TableStateProperty.STATESTORE_PROVIDER_CACHE_SIZE;
import static sleeper.core.properties.table.TableProperty.TABLE_ID;

/**
 * Caches Sleeper table state store objects up to a maximum size. If the cache is full, the LRU state store objects
 * are removed from the cache. An instance of this class cannot be used concurrently in multiple threads,
 * as the cache is not thread-safe.
 */
public class StateStoreProvider {
    private final int cacheSize;
    private final Factory stateStoreFactory;
    private final Map<String, StateStore> tableIdToStateStoreCache = new HashMap<>();
    private final Queue<String> tableIds = new LinkedList<>();

    public StateStoreProvider(InstanceProperties instanceProperties, Factory stateStoreFactory) {
        this(instanceProperties.getInt(STATESTORE_PROVIDER_CACHE_SIZE), stateStoreFactory);
    }

    public StateStoreProvider(int cacheSize, Factory stateStoreFactory) {
        this.cacheSize = cacheSize;
        this.stateStoreFactory = stateStoreFactory;
    }

    /**
     * Creates a state store provider with no limit to the number of tables that can be cached. The cache must be
     * managed explicitly by calling methods on the provider.
     *
     * @param  stateStoreFactory the factory to create state store objects
     * @return                   the provider
     */
    public static StateStoreProvider noCacheSizeLimit(Factory stateStoreFactory) {
        return new StateStoreProvider(-1, stateStoreFactory);
    }

    /**
     * Retrieves or creates the state store client for the given Sleeper table.
     *
     * @param  tableProperties the Sleeper table properties
     * @return                 the state store
     */
    public StateStore getStateStore(TableProperties tableProperties) {
        String tableId = tableProperties.get(TABLE_ID);
        if (!tableIdToStateStoreCache.containsKey(tableId)) {
            if (tableIdToStateStoreCache.size() == cacheSize) {
                removeLeastRecentlyUsedStateStoreFromCache(tableId);
            }
            StateStore stateStore = stateStoreFactory.getStateStore(tableProperties);
            tableIdToStateStoreCache.put(tableId, stateStore);
        }
        tableIds.remove(tableId);
        tableIds.add(tableId);
        return tableIdToStateStoreCache.get(tableId);
    }

    /**
     * Returns the Sleeper table IDs of the cached state stores.
     *
     * @return the Sleeper table IDs
     */
    public Set<String> getTableIdsOfCachedStateStores() {
        return new HashSet<>(tableIdToStateStoreCache.keySet());
    }

    /**
     * Remove the least recently used table's state store from the cache.
     *
     * @return the ID of the Sleeper table that was removed from the cache
     */
    public Optional<String> removeLeastRecentlyUsedStateStoreFromCache() {
        return removeLeastRecentlyUsedStateStoreFromCache(Set.of());
    }

    /**
     * Remove the least recently used table's state store from the cache.
     *
     * @param  tableIdToKeep the ID of a Sleeper table that should not be removed from the cache
     * @return the ID of the Sleeper table that was removed from the cache
     */
    public Optional<String> removeLeastRecentlyUsedStateStoreFromCache(String tableIdToKeep) {
        return removeLeastRecentlyUsedStateStoreFromCache(Set.of(tableIdToKeep));
    }

    /**
     * Remove the least recently used table's state store from the cache.
     *
     * @param  tableIdsToKeep the IDs of the Sleeper tables that should not be removed from the cache
     * @return the ID of the Sleeper table that was removed from the cache
     */
    public Optional<String> removeLeastRecentlyUsedStateStoreFromCache(Collection<String> tableIdsToKeep) {
        Optional<String> tableIdToRemove = tableIds.stream()
            .filter(tableId -> !tableIdsToKeep.contains(tableId))
            .findFirst();

        if (tableIdToRemove.isPresent()) {
            removeStateStoreFromCache(tableIdToRemove.get());
        }

        return tableIdToRemove;
    }

    /**
     * Remove a specific table's state store from the cache.
     *
     * @param  tableId the Sleeper table ID
     * @return         true if the state store for the requested Sleeper table was in the cache and has been removed
     */
    public boolean removeStateStoreFromCache(String tableId) {
        return tableIdToStateStoreCache.remove(tableId) != null
                && tableIds.remove(tableId);
    }

    /**
     * Creates an instance of the state store client for a Sleeper table. Implemented by {@link StateStoreFactory}.
     */
    public interface Factory {

        /**
         * Creates a state store client.
         *
         * @param  tableProperties the Sleeper table properties
         * @return                 the state store
         */
        StateStore getStateStore(TableProperties tableProperties);
    }
}
