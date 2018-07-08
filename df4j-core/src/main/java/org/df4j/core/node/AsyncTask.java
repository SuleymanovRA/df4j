/*
 * Copyright 2011 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.df4j.core.node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AsyncTask is an Asynchronous Procedure Call.
 *
 * It consists of asynchronous connectors, implemented as inner classes,
 * user-defined asynchronous procedure, and a asyncTask mechanism to call that procedure
 * using supplied {@link java.util.concurrent.Executor} as soon as all connectors are unblocked.
 *
 * This class contains connectors for following protocols:
 *  - scalar messages
 *  - message stream (without back pressure)
 *  - permit stream
 */
public class AsyncTask implements Runnable {

    /**
     * the set of all b/w Pins
     */
    protected final HashSet<Lock> locks = new HashSet<>();
    /**
     * the set of all colored Pins
     */
    protected final ArrayList<Connector> connectors = new ArrayList<>();
    /**
     * total number of created pins
     */
    protected AtomicInteger pinCount = new AtomicInteger();
    /**
     * total number of created pins
     */
    protected AtomicInteger blockedPinCount = new AtomicInteger();

    protected volatile boolean started = false;
    protected volatile boolean stopped = false;

    protected Executor executor = ForkJoinPool.commonPool();

    protected Runnable target = null;

    /**
     * blocked initially, until {@link #start} called.
     * blocked when this actor goes to executor, to ensure serial execution of the act() method.
     */
    protected ControlLock controlLock = new ControlLock();

    public AsyncTask() {
    }

    public AsyncTask(Runnable target) {
        this.target = target;
    }

    public void setTarget(Runnable target) {
        this.target = target;
    }

    public synchronized void start() {
        if (stopped) {
            return;
        }
        started = true;
        controlLock.turnOn();
    }

    public synchronized void start(Executor executor) {
        setExecutor(executor);
        start();
    }

    public synchronized void stop() {
        stopped = true;
        controlLock.turnOff();
    }

    /**
     * assigns Executor
     */
    public void setExecutor(Executor exec) {
        this.executor = exec;
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
 //       getExecutor().execute(this);
        run();
    }

    public boolean isStarted() {
        return started;
    }

    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    public synchronized Object[] consumeTokens() {
        if (!isStarted()) {
            throw new IllegalStateException("not started");
        }
        locks.forEach(lock -> lock.purge());
        Object[] args = new Object[connectors.size()];
        for (int k=0; k<connectors.size(); k++) {
            AsyncTask.Connector connector = connectors.get(k);
            args[k] = connector.next();
        }
        return args;
    }

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

        public void turnOn() {
            if (!blocked) {
                return;
            }
            blocked = false;
            long res = blockedPinCount.decrementAndGet();
            if (res == 0) {
                fire();
            }
        }

        abstract protected void register();

        abstract protected void unRegister();
    }

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
     * Basic class for all connectors (places for tokens).
     * Asynchronous version of binary semaphore.
     * <p>
     * initially in non-blocked state
     */
    public abstract class Connector<T> extends BaseLock {

        public Connector(boolean blocked) {
            super(blocked);
        }

        public Connector() {
            super();
        }

        @Override
        protected void register() {
            if (isStarted()) {
                throw new IllegalStateException("cannot register connector after start");
            }
            connectors.add(this);
        }

        protected void unRegister() {
            if (isStarted()) {
                throw new IllegalStateException("cannot unregister connector after start");
            }
            if (blocked) {
                turnOn();
            }
            connectors.remove(this);
        }

        /** removes and return next token */
        public abstract T next();
    }

    protected class ControlLock extends Lock {
    }
}