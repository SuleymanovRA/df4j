package org.df4j.core.impl;

import org.df4j.core.spi.Activity;
import org.df4j.core.spi.Connector;
import org.df4j.core.spi.Transition;
import org.df4j.core.spi.messagescalar.Port;
import org.df4j.core.spi.Registration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Jim on 02-Jun-17.
 */
public class ArrayTransition implements Transition {

    /**
     * main scale of bits, one bit per pin
     * when pinBits becomes 0, transition fires
     */
    private AtomicLong pinBits = new AtomicLong();
    private int pinCount = 0;
    /** the list of all Pins */
    private ArrayList<Registration> accounts = new ArrayList<>(4);

    /**
     * locks pin by setting it to 1
     * called when a token is consumed and the pin become empty
     *
     * @param pinBit
     */
    protected void _turnOff(int pinBit) {
        pinBits.updateAndGet(pinBits -> pinBits | pinBit);
    }

    /**
     * turns pinBit on, i.e. to 0
     * if pin scale makes all zeros, blocks control pin
     *
     * @param pinBit
     * @return true if all transition become ready
     */
    protected boolean _turnOn(int pinBit) {
        long res =  pinBits.updateAndGet(pinBits -> {
            if (pinBits == 0) {
                return 1;
            }
            pinBits = pinBits & ~pinBit;
            return pinBits;
        });
        return res == 0;
    }

    /**
     * invoked when all transition transition are ready,
     * and method run() is to be invoked.
     * Safe way is to submit this instance as a Runnable to an Executor.
     * Fast way is to invoke it directly, but make sure the chain of
     * direct invocations is short to avoid stack overflow.
     */

    protected ActivityPlace activiactivityPlace = new ActivityPlace();

    /**
     * invoked when all transition transition are ready,
     * and method run() is to be invoked.
     * Safe way is to submit this instance as a Runnable to an Executor.
     * Fast way is to invoke it directly, but make sure the chain of
     * direct invocations is short to avoid stack overflow.
     */
    protected void fire() {
        activiactivityPlace.take().start();
    }

    @Override
    public Registration register(Connector connector) {
        Account account = new Account(connector);
        accounts.add(account);
        return account;
    }

    @Override
    public void postActivity(Activity activity) {
        activiactivityPlace.post(activity);
    }

    @Override
    public Iterator<Registration> iterator() {
        return accounts.iterator();
    }

    /*==================================================*/

    /**
     * by default, initially in blocing state
     */
    public class Account implements Registration {

        private final int pinBit; // distinct for all other transition of the node

        final Connector connector;

        protected Account(Connector connector) {
            this.connector = connector;
            if (pinCount == 64) {
                throw new IllegalStateException("only 64 transition could be created");
            }
            pinBit = 1 << (pinCount++); // assign next pin number
            accounts.add(this);
        }

        public void turnOn() {
            boolean on = ArrayTransition.this._turnOn(pinBit);
            if (on) {
                fire();
            }
        }

        public void purge() {
            connector.purge();
        }

        /**
         * lock pin by setting it to 1
         * called when a token is consumed and the pin become empty
         */
        public void turnOff() {
            _turnOff(pinBit);
        }

    }

    public class ActivityPlace implements Port<Activity>, Connector {

        private final Registration registration;

        /** extracted token */
        public Activity activity = null;

        protected ActivityPlace() {
            registration = register(this);
            registration.turnOff();
        }

        public synchronized Activity take() {
            Activity activity = this.activity;
            if (activity == null) {
                throw new IllegalStateException();
            }
            this.activity = null;
            registration.turnOff();
            return activity;
        }

        /**
         *  @throws NullPointerException
         *  @throws IllegalStateException
         */
        @Override
        public synchronized void post(Activity activity) {
            if (activity == null) {
                throw new IllegalArgumentException();
            }
            if (this.activity != null) {
                throw new IllegalStateException("activiactivityPlace set already");
            }
            this.activity = activity;
            registration.turnOn();
        }

        /**
         * pin bit remains ready
         */
        @Override
        public void purge() {
        }
    }
}
