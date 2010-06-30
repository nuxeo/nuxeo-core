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
 *     bstefanescu
 */
package org.nuxeo.ecm.core.client.sample;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.repository.RepositoryInstance;
import org.nuxeo.ecm.core.client.NuxeoClient;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class HelloServer {

    public static void main(String[] args) throws Exception {
        NuxeoClient client = NuxeoClient.getInstance();
        RepositoryInstance repo = null;
        try {
            client.tryConnect("localhost", 62474);
            repo =client.openRepository();
            final DocumentModel rootDocument = repo.getRootDocument();
            System.out.println("Hello Server! Here is the repository root: "+rootDocument);
        } finally {
            repo.close();
            client.tryDisconnect();
        }
    }

}
