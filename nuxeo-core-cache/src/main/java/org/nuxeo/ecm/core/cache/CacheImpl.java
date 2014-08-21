/*
 * (C) Copyright 2014 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
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

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Default in memory implementation for cache management based on guava
 * 
 * @since 5.9.6
 */
public class CacheImpl extends AbstractCache {

    private static final Log log = LogFactory.getLog(CacheImpl.class);
    
    protected Cache<String, Serializable> cache = null;
    
    
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
    
    public Cache<String, Serializable> getCache()
    {
        createCache();
        return cache;
    }

    @Override
    public Serializable get(String key) {
        return getCache().getIfPresent(key);
    }


    @Override
    public void invalidate(String key) {
        getCache().invalidate(key);
    }

    @Override
    public void invalidateAll() {
        getCache().invalidateAll();
    }

    @Override
    public void put(String key, Serializable value) {
        getCache().put(key, value);
    }
    


}
