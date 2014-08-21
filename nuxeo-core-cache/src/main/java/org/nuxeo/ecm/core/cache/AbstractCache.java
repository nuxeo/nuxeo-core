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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.services.event.Event;

/**
 * Abstract class to be extended to provide new cache technologies
 *
 * @since 5.9.6
 */
public abstract class AbstractCache implements Cache {

    private static final Log log = LogFactory.getLog(AbstractCache.class);

    protected String name = null;

    protected Integer maxSize = 0;

    protected Integer ttl = 0;

    protected Integer concurrencyLevel = 0;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public Integer getConcurrencyLevel() {
        return concurrencyLevel;
    }

    public void setConcurrencyLevel(Integer concurrencyLevel) {
        this.concurrencyLevel = concurrencyLevel;
    }

    @Override
    public boolean aboutToHandleEvent(Event event) {
        return false;
    }

    @Override
    public void handleEvent(Event event) {
        final String id = event.getId();
        if (CacheService.INVALIDATE_ALL.equals(id)) {
            try {
                invalidateAll();
            } catch (Exception e) {
                log.error(
                        String.format("Failed to invalidate cache '%s'", name),
                        e);
                throw new RuntimeException(e);
            }
        }
    }

}
