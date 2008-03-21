/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 *
 * $Id$
 */

package org.nuxeo.ecm.core.security;

/**
 * Abstract security policy
 *
 * @author Anahide Tchertchian
 */
public abstract class AbstractSecurityPolicy implements SecurityPolicy {

    /**
     * Returns true if permission to check is in the given list of all
     * permissions or groups of permissions.
     */
    public boolean isPermissionImplied(String permissionToCheck,
            String[] resolvedPermissions) {
        boolean res = false;
        if (resolvedPermissions != null) {
            for (String perm : resolvedPermissions) {
                if (permissionToCheck.equals(perm)) {
                    res = true;
                    break;
                }
            }
        }
        return res;
    }

}
