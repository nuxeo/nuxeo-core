/*
 * (C) Copyright 2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id $
 */
package org.nuxeo.ecm.core.api;

import java.util.List;

/**
 * @author ogrisel
 * 
 * @param <E> the type of results element (e.g. DocumentModel, DashboardItem,
 *            ...)
 */
public interface SelectableResultsProvider<E> extends ResultsProvider<E> {

    /**
     * Wrapper interface to wrap a results element into a selectable component
     * 
     * @param <E> the type of the wrapped result element (e.g. DocumentModel,
     *            DashboardItem, ...)
     */
    public static interface SelectableResultItem<E> {

        /**
         * @return the SelectableResultsProvider instance that holds the
         *         selection
         */
        SelectableResultsProvider<E> getProvider();

        boolean isSelected();

        void select();

        void unselect();

        /**
         * @return the wrapped result element
         */
        E getData();

    }

    /**
     * @return the list of items of the current page wrapped into
     *         SelectableResultItem objects that provide selection API
     * @throws ResultsProviderException
     */
    List<SelectableResultItem<E>> getSelectableCurrentPage()
            throws ResultsProviderException;

    /**
     * @return the list of items that are marked as selected on any page
     * @throws ResultsProviderException
     */
    List<E> getSelectedResultItems() throws ResultsProviderException;

    /*
     * SelectionListener registration and API
     */

    public static interface SelectionListener<E> {

    }

    void addSelectionListener(SelectionListener<E> listener);

    void removeSelectModelListener(SelectionListener<E> listener);

    SelectionListener<E>[] getSelectModelListeners();

}
