/*
 * (C) Copyright 2007-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core.storage.sql;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.HashMap;

import org.nuxeo.ecm.core.storage.StorageException;

/**
 * A rich value corresponding to one row or a collection of rows in a table.
 * <p>
 * The table is identified by its table name, which the {@link Mapper} knows
 * about.
 * <p>
 * The id of the fragment is distinguished internally from other columns. For
 * fragments corresponding to created data, the initial id is a temporary one,
 * and it will be changed after database insert.
 *
 * @author Florent Guillaume
 */
public abstract class Fragment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The id. If the fragment was just created, and database id generation is
     * used, the initial temporary id will be changed at save time to its final
     * value.
     */
    private Serializable id;

    /**
     * The possible states of a fragment.
     */
    public static enum State {
        /**
         * The fragment is not attached to a persistence context.
         */
        DETACHED, // first is default

        /**
         * The fragment has been read and found to be absent in the database. It
         * contains default data (usually {@code null}). It lives in the
         * context's pristine map. Upon modification, the state will change to
         * {@link #CREATED}.
         */
        ABSENT,

        /**
         * The fragment exists in the database but hasn't been changed yet. It
         * lives in the context's pristine map. Upon modification, the state
         * will change to {@link #MODIFIED}.
         */
        PRISTINE,

        /**
         * The fragment does not exist in the database and will be inserted upon
         * save. It lives in the context's modified map. Upon save it will be
         * inserted in the database and the state will change to
         * {@link #PRISTINE}.
         */
        CREATED,

        /**
         * The fragment has been modified. It lives in the context's modified
         * map. Upon save the database will be updated and the state will change
         * to {@link #CREATED}.
         */
        MODIFIED,

        /**
         * The fragment has been deleted. It lives in the context's modified
         * map. Upon save it will be deleted from the database and the state
         * will change to {@link #DETACHED}.
         */
        DELETED,

        /**
         * The fragment has been invalidated by a modification or creation. Any
         * access must refetch it. It lives in the context's pristine map.
         */
        INVALIDATED_MODIFIED,

        /**
         * The fragment has been invalidated by a deletion. It lives in the
         * context's pristine map.
         */
        INVALIDATED_DELETED;
    }

    private transient State state; // default is DETACHED

    private transient Context context;

    /**
     * Constructs an empty {@link Fragment} with the given id (which may be a
     * temporary one).
     *
     * @param id the id
     * @param state the initial state for the fragment
     * @param context the persistence context to which the fragment is tied, or
     *            {@code null}
     */
    public Fragment(Serializable id, State state, Context context) {
        this.id = id;
        this.state = state;
        this.context = context;
        switch (state) {
        case DETACHED:
            if (context != null) {
                throw new IllegalArgumentException();
            }
            break;
        case CREATED:
            context.modified.put(id, this);
            break;
        case ABSENT:
        case PRISTINE:
            context.pristine.put(id, this);
            break;
        case MODIFIED:
        case DELETED:
        case INVALIDATED_MODIFIED:
        case INVALIDATED_DELETED:
            throw new IllegalArgumentException(state.toString());
        }
    }

    /**
     * Gets the context.
     *
     * @return the context
     */
    protected Context getContext() {
        return context;
    }

    /**
     * Gets the table name.
     *
     * @return the table name
     */
    public String getTableName() {
        return context.getTableName();
    }

    /**
     * Gets the state.
     *
     * @return the state
     */
    public State getState() {
        return state;
    }

    /**
     * Sets the id. This only used at most once to change a temporary id to the
     * persistent one.
     *
     * @param id the new persistent id
     */
    public void setId(Serializable id) {
        assert state == State.CREATED;
        assert id != null;
        this.id = id;
    }

    /**
     * Gets the id.
     *
     * @return the id
     */
    public Serializable getId() {
        return id;
    }

    /**
     * Refetches this fragment from the database. Needed when an invalidation
     * has been received and the fragment is accessed again.
     *
     * @return the new state, {@link State#PRISTINE} or {@link State#ABSENT}
     * @throws StorageException
     */
    protected abstract State refetch() throws StorageException;

    /**
     * Detaches the fragment from its persistence context.
     */
    protected void detach() {
        state = State.DETACHED;
        context = null;
    }

    /**
     * Checks that access to the fragment is possible. Called internally before
     * a get, so that invalidated fragments can be refetched.
     *
     * @throws StorageException
     */
    protected void accessed() throws StorageException {
        switch (state) {
        case DETACHED:
        case ABSENT:
        case PRISTINE:
        case CREATED:
        case MODIFIED:
        case DELETED:
            break;
        case INVALIDATED_MODIFIED:
            state = refetch(); // updates state
            break;
        case INVALIDATED_DELETED:
            throw new ConcurrentModificationException(
                    "Accessing a concurrently deleted value");
        }
    }

    /**
     * Marks the fragment modified. Called internally after a put/set.
     */
    protected void modified() {
        switch (state) {
        case ABSENT:
            context.pristine.remove(id);
            context.modified.put(id, this);
            state = State.CREATED;
            break;
        case INVALIDATED_MODIFIED:
            // can only happen if overwrite all invalidated (array)
            // fall through
        case PRISTINE:
            context.pristine.remove(id);
            context.modified.put(id, this);
            state = State.MODIFIED;
            break;
        case DETACHED:
        case CREATED:
        case MODIFIED:
        case DELETED:
            break;
        case INVALIDATED_DELETED:
            throw new ConcurrentModificationException(
                    "Modifying a concurrently deleted value");
        }
    }

    /**
     * Marks the fragment invalidated from a modification. Called during
     * post-commit invalidation.
     */
    protected void invalidateModified() {
        switch (state) {
        case ABSENT:
        case PRISTINE:
            state = State.INVALIDATED_MODIFIED;
            break;
        case INVALIDATED_MODIFIED:
        case INVALIDATED_DELETED:
            break;
        case DETACHED:
        case CREATED:
        case MODIFIED:
        case DELETED:
            // incoherent with the pristine map
            throw new AssertionError(this);
        }
    }

    /**
     * Marks the fragment invalidated from a deletion. Called during post-commit
     * invalidation.
     */
    protected void invalidateDeleted() {
        switch (state) {
        case ABSENT:
        case PRISTINE:
        case INVALIDATED_MODIFIED:
            state = State.INVALIDATED_DELETED;
            break;
        case INVALIDATED_DELETED:
            break;
        case DETACHED:
        case CREATED:
        case MODIFIED:
        case DELETED:
            // incoherent with the pristine map
            throw new AssertionError(this);
        }
    }

    /**
     * Marks the fragment deleted. Called after a remove.
     */
    public void markDeleted() {
        switch (state) {
        case DETACHED:
            break;
        case ABSENT:
        case INVALIDATED_DELETED:
            context.pristine.remove(id);
            context = null;
            state = State.DETACHED;
            break;
        case CREATED:
            context.modified.remove(id);
            context = null;
            state = State.DETACHED;
            break;
        case PRISTINE:
        case INVALIDATED_MODIFIED:
            context.pristine.remove(id);
            context.modified.put(id, this);
            state = State.DELETED;
            break;
        case MODIFIED:
            state = State.DELETED;
            break;
        case DELETED:
            throw new AssertionError(this);
        }
    }

    /**
     * Marks the (created/modified) fragment pristine. Called after a save.
     * <p>
     * Lets the called move from the modified to the pristine map, as it's
     * inside an iterator over modified.
     */
    protected void markPristine() {
        switch (state) {
        case CREATED:
        case MODIFIED:
            state = State.PRISTINE;
            break;
        case ABSENT:
        case PRISTINE:
        case DELETED:
        case DETACHED:
        case INVALIDATED_MODIFIED:
        case INVALIDATED_DELETED:
            // incoherent with the pristine map + expected state
            throw new AssertionError(this);
        }
    }

}

/**
 * A fragments map holds all {@link Fragment}s for non-main tables.
 */
class FragmentsMap extends HashMap<String, Fragment> {

    private static final long serialVersionUID = 1L;

}

/**
 * Utility class grouping a main {@link Fragment} with a related hierarchy
 * {@link Fragment} and additional fragments.
 * <p>
 * This is all the data needed to describe a {@link Node}.
 */
class FragmentGroup {

    public final SimpleFragment hier;

    public final SimpleFragment main;

    public final FragmentsMap fragments;

    public FragmentGroup(SimpleFragment main, SimpleFragment hier,
            FragmentsMap fragments) {
        this.main = main;
        this.hier = hier;
        this.fragments = fragments;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + hier + ", " + main + ", " +
                fragments + ')';
    }

}
