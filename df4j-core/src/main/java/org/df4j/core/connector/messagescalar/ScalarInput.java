package org.df4j.core.connector.messagescalar;

import org.df4j.core.node.AsyncTaskBase;

import java.util.Iterator;

/**
 * Token storage with standard Subscriber<T> interface.
 * It has place for only one token.
 *
 * @param <T>
 *            type of accepted tokens.
 */
public class ScalarInput<T> extends ConstInput<T> implements Iterator<T> {
    protected AsyncTaskBase actor;
    protected boolean pushback = false; // if true, do not consume

    public ScalarInput(AsyncTaskBase actor) {
        super(actor);
        this.actor = actor;
    }

    // ===================== backend

    protected void pushback() {
        if (pushback) {
            throw new IllegalStateException();
        }
        pushback = true;
    }

    protected synchronized void pushback(T value) {
        if (pushback) {
            throw new IllegalStateException();
        }
        pushback = true;
        this.value = value;
    }

    @Override
    public boolean hasNext() {
        return !isDone();
    }

    @Override
    public T next() {
        if (exception != null) {
            throw new RuntimeException(exception);
        }
        if (value == null) {
            throw new IllegalStateException();
        }
        T res = value;
        if (pushback) {
            pushback = false;
            // value remains the same, the pin remains turned on
        } else {
            value = null;
            turnOff();
        }
        return res;
    }
}
