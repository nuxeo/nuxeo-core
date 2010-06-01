/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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
package org.nuxeo.ecm.core.storage.sql.net;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.nuxeo.ecm.core.storage.StorageException;
import org.nuxeo.ecm.core.storage.sql.Mapper;
import org.nuxeo.ecm.core.storage.sql.Repository;
import org.nuxeo.ecm.core.storage.sql.Session;

/**
 * Will execute in a separate thread the commands passed through
 * {@link #methodCalls}.
 */
public class MapperInvoker extends Thread {

    private static final String INVOKER_INIT = "__init";

    private static final String INVOKER_CLOSE = "__close";

    private static final Map<String, Method> mapperMethods = new HashMap<String, Method>();
    static {
        for (Method m : Mapper.class.getMethods()) {
            mapperMethods.put(m.getName(), m);
        }
    }

    protected static class MethodCall {
        String methodName;

        Object[] args;

        public MethodCall(String methodName, Object[] args) {
            this.methodName = methodName;
            this.args = args;
        }
    }

    protected static class MethodResult {
        Object result;

        public MethodResult(Object result) {
            this.result = result;
        }
    }

    private Repository repository;

    private Session session;

    private Mapper mapper;

    protected final BlockingQueue<MethodCall> methodCalls;

    protected final BlockingQueue<MethodResult> methodResults;

    public MapperInvoker(Repository repository, String name) throws Throwable {
        super(name);
        this.repository = repository;
        methodCalls = new LinkedBlockingQueue<MethodCall>(1);
        methodResults = new LinkedBlockingQueue<MethodResult>(1);
        start();
        init();
    }

    @Override
    public void run() {
        try {
            while (true) {
                MethodCall call = methodCalls.take();
                Object res;
                try {
                    res = localCall(call.methodName, call.args);
                } catch (InvocationTargetException e) {
                    res = e.getCause();
                } catch (Exception e) {
                    res = e;
                }
                methodResults.put(new MethodResult(res));
            }
        } catch (InterruptedException e) {
            // end
        }
    }

    // called in the main thread
    public void init() throws Throwable {
        call(INVOKER_INIT);
    }

    // called in the main thread
    public void close() throws Throwable {
        call(INVOKER_CLOSE);
        interrupt();
        join();
    }

    // called in the main thread
    public Object call(String methodName, Object... args) throws Throwable {
        methodCalls.put(new MethodCall(methodName, args));
        Object res = methodResults.take().result;
        if (res instanceof Throwable) {
            throw (Throwable) res;
        }
        return res;
    }

    protected Object localCall(String methodName, Object[] args)
            throws Exception {
        if (methodName == INVOKER_INIT) { // == is ok
            session = repository.getConnection();
            mapper = session.getMapper();
            return null;
        } else if (methodName == INVOKER_CLOSE) { // == is ok
            session.close();
            mapper = null;
            return null;
        } else if ("close".equals(methodName)) {
            // ignored, done by above invoker close, on the session
            // (we must not close the mapper directly as it may be in a pool)
            return null;
        }
        Method method = mapperMethods.get(methodName);
        if (method == null) {
            throw new StorageException("Unknown Mapper method: " + methodName);
        } else {
            return method.invoke(mapper, args);
        }
    }

}
