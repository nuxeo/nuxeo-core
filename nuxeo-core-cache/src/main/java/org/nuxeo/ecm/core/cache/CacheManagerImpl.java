/*
 * (C) Copyright 2014 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Maxime Hilaire
 *
 */
package org.nuxeo.ecm.core.cache;

import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.services.event.Event;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Default in memory implementation for cache management based on guava
 * 
 * @author Maxime Hilaire
 * @param <K,V>
 * @since 5.9.6
 */
public class CacheManagerImpl<K,V> extends AbstractCacheManager<K,V> {

    private static final Log log = LogFactory.getLog(CacheManagerImpl.class);
    
    protected Cache<K,V> cache = null;
    
    @Override
    public boolean aboutToHandleEvent(Event event) {
        return true;
    }

    @Override
    public void handleEvent(Event event) {
        final String id = event.getId();
        if (CacheManagerService.INVALIDATE_ALL.equals(id)) {
            try {
                invalidateAll();
            } catch (Exception e) {
                log.error("Failed invalidate all cache", e);
            }
        }
    }
    
    private void createCache()
    {
        if(this.cache == null)
        {
            cache = CacheBuilder.newBuilder().concurrencyLevel(
                concurrencyLevel).maximumSize(
                maxSize).expireAfterWrite(
                ttl, TimeUnit.MINUTES).build();
        }
        
    }
    
    public Cache<K, V> getCache()
    {
        createCache();
        return cache;
    }

    @Override
    public V get(K key) {
        return getCache().getIfPresent(key);
    }


    @Override
    public void invalidate(K key) {
        getCache().invalidate(key);
    }

    @Override
    public void invalidateAll() {
        getCache().invalidateAll();
    }

    @Override
    public void put(K key, V value) {
        getCache().put(key, value);
    }
    


}
