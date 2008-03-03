/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id: PermissionUIItemDescriptor.java 28609 2008-01-09 16:38:30Z sfermigier $
 */

package org.nuxeo.ecm.core.security;

import org.nuxeo.common.xmap.annotation.XContent;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

@XObject("item")
public class PermissionUIItemDescriptor {

    @XNode("@show")
    private Boolean show;

    @XNode("@order")
    private Integer order;

    private String permission = "";

    @XContent
    protected void setPermission(String permission) {
        this.permission = permission.trim();
    }

    public PermissionUIItemDescriptor() {}

    public PermissionUIItemDescriptor(PermissionUIItemDescriptor referenceDescriptor) {
        show = referenceDescriptor.show;
        order = referenceDescriptor.order;
        permission = referenceDescriptor.permission;
    }

    public int getOrder() {
        if (order == null) {
            // default order
            return 0;
        } else {
            return order;
        }
    }

    public boolean isShown() {
        if (show == null) {
            // permission items are shown by default
            return true;
        } else {
            return show;
        }
    }

    public String getPermission() {
        return permission;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof PermissionUIItemDescriptor) {
            PermissionUIItemDescriptor otherPid = (PermissionUIItemDescriptor) other;
            if (!permission.equals(otherPid.permission)) {
                return false;
            }
            if (show != null) {
                if (!show.equals(otherPid.show)) {
                    return false;
                }
            } else {
                if (otherPid.show != null) {
                    return false;
                }
            }
            if (order != null) {
                if (!order.equals(otherPid.order)) {
                    return false;
                }
            } else {
                if (otherPid.order != null) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public void merge(PermissionUIItemDescriptor pid) throws Exception {
        // sanity check
        if (!permission.equals(pid.permission)) {
            // TODO: use a dedicated Nuxeo Runtime / OSGi exception here
            throw new Exception(String.format(
                    "cannot merge permission item '%s' with '%s'", permission,
                    pid.permission));
        }
        // do not merge unset attributes
        show = pid.show != null ? pid.show : show;
        order = pid.order != null ? pid.order : order;
    }

    @Override
    public String toString() {
        return String.format("PermissionUIItemDescriptor[%s]", permission);
    }

}
