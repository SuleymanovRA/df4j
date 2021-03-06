/*
 * Copyright 2011 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.df4j.core.tasknode;

import org.df4j.core.util.executor.DirectExecutor;
import org.df4j.core.util.executor.CurrentThreadExecutor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AsyncProc is an Asynchronous Procedure.
 *
 * It consists of asynchronous connectors, implemented as inner classes,
 * user-defined asynchronous procedure, and a mechanism to call that procedure
 * using supplied {@link java.util.concurrent.Executor} as soon as all connectors are unblocked.
 *
 * This class contains base classes for locks and connectors
 */
public abstract class AsyncProc implements Runnable {
    public static final DirectExecutor directExecutor = DirectExecutor.directExecutor;
    public static final Executor syncExec = CurrentThreadExecutor.CURRENT_THREAD_EXECUTOR;
    public static final Executor asyncExec = ForkJoinPool.commonPool();
    public static final Executor newThreadExec = (Runnable r)->new Thread(r).start();

    /**
     * the set of all b/w Pins
     */
    protected final HashSet<Lock> locks = new HashSet<>();
    /**
     * the set of all colored Pins, to form array of arguments
     */
    protected final ArrayList<AsyncParam> asyncParams = new ArrayList<>();
    /**
     * total number of created pins
     */
    protected AtomicInteger pinCount = new AtomicInteger();
    /**
     * total number of created pins
     */
    protected AtomicInteger blockedPinCount = new AtomicInteger();

    protected Executor executor = asyncExec;

    public void setExecutor(Executor exec) {
        if (exec == null) {
            this.executor = CurrentThreadExecutor.CURRENT_THREAD_EXECUTOR;
        } else {
            this.executor = exec;
        }
    }

    public Executor getExecutor() {
        return executor;
    }

    /**
     * invoked when all asyncTask asyncTask are ready,
     * and method run() is to be invoked.
     * Safe way is to submit this instance as a Runnable to an Executor.
     * Fast way is to invoke it directly, but make sure the chain of
     * direct invocations is short to avoid stack overflow.
     */
    protected void fire() {
        getExecutor().execute(this);
    }

    protected abstract boolean isStarted();

    /**
     * Basic class for all locs and connectors (places for tokens).
     * Asynchronous version of binary semaphore.
     * <p>
     * initially in non-blocked state
     */
    private abstract class BaseLock {
        int pinNumber; // distinct for all other connectors of this node
        boolean blocked;

        public BaseLock(boolean blocked) {
            this.pinNumber = pinCount.getAndIncrement();
            this.blocked = blocked;
            if (blocked) {
                blockedPinCount.incrementAndGet();
            }
            register();
        }

        /**
         * by default, initially in blocked state
         */
        public BaseLock() {
            this(true);
        }

        public boolean isBlocked() {
            return blocked;
        }

        /**
         * locks the pin
         * called when a token is consumed and the pin become empty
         */
        public void turnOff() {
            if (blocked) {
                return;
            }
            blocked = true;
            blockedPinCount.incrementAndGet();
        }

        public boolean turnOn() {
            if (!blocked) {
                return false;
            }
            blocked = false;
            long res = blockedPinCount.decrementAndGet();
            if (res == 0) {
                fire();
            }
            return true;
        }

        abstract protected void register();

        abstract protected void unRegister();
    }


    /**
     * Basic class for all permission parameters (places for black/white tokens).
     * Asynchronous version of binary semaphore.
     * <p>
     * initially in non-blocked state
     */
    public class Lock extends BaseLock {

        public Lock(boolean blocked) {
            super(blocked);
        }

        public Lock() {
            super();
        }

        @Override
        protected void register() {
            locks.add(this);
        }

        protected void unRegister() {
            if (blocked) {
                turnOn();
            }
            locks.remove(this);
        }

        /**
         * Executed after token processing (method act). Cleans reference to
         * value, if any. Signals to set state to off if no more tokens are in
         * the place. Should return quickly, as is called from the actor's
         * synchronized block.
         */
        public void purge() {
        }
    }

    /**
     * Basic class for all valued parameters (places for colored tokens).
     * <p>
     * initially in blocked state
     */
    public abstract class AsyncParam<T> extends BaseLock {

        public AsyncParam(boolean blocked) {
            super(blocked);
        }

        public AsyncParam() {
            super();
        }

        @Override
        protected void register() {
            if (isStarted()) {
                throw new IllegalStateException("cannot register connector after start");
            }
            asyncParams.add(this);
        }

        protected void unRegister() {
            if (isStarted()) {
                throw new IllegalStateException("cannot unregister connector after start");
            }
            if (blocked) {
                turnOn();
            }
            asyncParams.remove(this);
        }

        /** removes and return next token
         *
         * @return the next token
         */
        public abstract T next();
    }

    public class CompletablePromise<R> extends org.df4j.core.simplenode.messagescalar.CompletablePromise<R> {
        public CompletablePromise() {
            super(AsyncProc.this);
        }
    }

}