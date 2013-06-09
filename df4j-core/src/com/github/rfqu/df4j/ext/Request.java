/*
 * Copyright 2011 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.rfqu.df4j.ext;

import com.github.rfqu.df4j.core.Callback;
import com.github.rfqu.df4j.core.ListenableFuture;
import com.github.rfqu.df4j.core.Port;

/**
 * A message that carries callback port.
 * Similar to {@link ListenableFuture}, but callback port is of type {@link Port}<{@link T}>.
 * @param <T> actual type of Request (subclassed)
 * @param <R> type of result
 */
public class Request<T extends Request<T, R>, R> {
    protected boolean _hasValue=false;
    protected R result=null;
    protected Throwable exc=null;
    protected Port<T> replyTo=null;

    public Request() {
    }

    public Request(Port<T> replyTo) {
        this.replyTo = replyTo;
    }

    /** reinitialize
     */
    public void reset() {
        _hasValue=false;
        result = null;
        exc = null;
        replyTo=null;
    }

    /** 
     * sends itself to the destination
     * should be invoked from synchronized methods
     */
    @SuppressWarnings("unchecked")
    private void reply() {
        _hasValue=true;
        Port<T> replyToLoc = replyTo;
        if (replyToLoc != null) {
            replyTo=null; // avoid memory leak
            replyToLoc.post((T) this);
        }
    }

    /** sets the result and forwards to the destination
     * @param result
     */
    public synchronized void post(R result) {
        this.result=result;
        reply();
    }

    /** sets the error and forwards to the destination
     * @param exc
     */
    public synchronized void postFailure(Throwable exc) {
        this.exc=exc;
        reply();
    }

    @SuppressWarnings("unchecked")
    public synchronized void setListener(Port<T> replyTo) {
        if (_hasValue) {
            replyTo.post((T) this);
        } else {
            this.replyTo = replyTo;
        }
    }

    public void toCallback(Callback<R> handler) {
        if (exc == null) { // check exc, returned result may be null
            handler.post(result);
        } else {
            handler.postFailure(exc);
        }
    }
    
    public synchronized boolean isDone() {
        return _hasValue;
    }

    public synchronized R getResult() {
        return result;
    }

    public synchronized Throwable getExc() {
        return exc;
    }
}
