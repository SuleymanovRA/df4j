package org.df4j.core.impl;

import org.df4j.core.spi.Activity;
import org.df4j.core.spi.Connector;
import org.df4j.core.spi.Registration;
import org.df4j.core.spi.Transition;
import org.df4j.core.spi.messagescalar.Port;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jim on 02-Jun-17.
 */
public class ListTransition implements Transition {

    /**
     * main counter of blocked Pins.
     * when {@link #pinCount} becomes 0, transition fires
     */
    private AtomicInteger pinCount = new AtomicInteger(0);

    /* list of all pins */
    private LinkHead<Pin> pins = new LinkHead<Pin>();

    protected ActivityPlace activityPlace = new ActivityPlace();

    /**
     * invoked when all transition transition are ready,
     * and method run() is to be invoked.
     * Safe way is to submit this instance as a Runnable to an Executor.
     * Fast way is to invoke it directly, but make sure the chain of
     * direct invocations is short to avoid stack overflow.
     */
    protected void fire() {
        activityPlace.take().start();
    }

    @Override
    public Registration register(Connector connector) {
        return null;
    }

    @Override
    public void postActivity(Activity activity) {
        activityPlace.post(activity);
    }

    @Override
    public Iterator<Registration> iterator() {
        return pins.iterator();
    }

    /*==================================================*/

    protected class Link<E extends Link> implements Registration {
        Link prev, next;

        void inserAfter(Link predLink) {
            prev = predLink;
            next = predLink.next;
            predLink.next = this;
            next.prev = this;
        }

        void unLink() {
            prev.next=next;
            next.prev = prev;
        }

        @Override
        public void turnOn() {

        }

        @Override
        public void turnOff() {

        }

        @Override
        public void purge() {

        }
    }

    protected class LinkHead<E extends Link> extends Link<E> implements Iterable<Registration> {
        {prev = next = this;}

        @Override
        public Iterator<Registration> iterator() {
            return  new Iterator<Registration>() {
                Link<?> current = LinkHead.this;

                @Override
                public boolean hasNext() {
                    return current != LinkHead.this;
                }

                @Override
                public Registration next() {
                    current = current.next;
                    return current;
                }

                @Override
                public void remove() {
                    current.unLink();
                }
            };
        }

        public void add(E pin) {
            pin.inserAfter(this);
        }
    }

    /**
     * by defaukt, initially in blocing state
     */
    public class Pin extends Link<Pin> {
        boolean blocked;

        protected Pin(boolean blocked) {
            this.blocked = blocked;
            pins.add(this);
            if (blocked) {
                turnOff();
            }
        }

        public Pin() {
            this(true);
        }

        public void turnOn() {
            long res =  pinCount.decrementAndGet();
            if (res == 0) {
                fire();
            }
        }

        /**
         * lock pin by setting it to 1
         * called when a token is consumed and the pin become empty
         */
        public void turnOff() {
            pinCount.incrementAndGet();
        }

        /**
         * Executed after token processing (method act). Cleans reference to
         * activiactivityPlace, if any. Signals to set state to off if no more tokens are in
         * the place. Should return quickly, as is called from the actor's
         * synchronized block.
         */
        public void purge() {
        }
    }

    public class ActivityPlace extends Pin implements Port<Activity> {

        /** extracted token */
        public Activity activity = null;

        public synchronized Activity take() {
            Activity activity = this.activity;
            if (activity == null) {
                throw new IllegalStateException();
            }
            this.activity = null;
            turnOff();
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
            turnOn();
        }

        /**
         * pin bit remains ready
         */
        @Override
        public void purge() {
        }
    }
}
