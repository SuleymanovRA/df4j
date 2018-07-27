package org.df4j.core.boundconnector.messagescalar;

import org.df4j.core.tasknode.AsyncProc;

import java.util.function.BiConsumer;

/**
 * Token storage with standard Subscriber<T> interface. It has place for only one
 * token, which is never consumed.
 *
 * @param <T>
 *     type of accepted tokens.
 */
public class ConstInput<T> extends AsyncProc.AsyncParam<T>
        implements ScalarSubscriber<T>,
        BiConsumer<T, Throwable>  // to connect to a CompletionStage by whenComplete
{
    protected SimpleSubscription subscription;
    protected boolean closeRequested = false;
    protected boolean cancelled = false;

    /** extracted token */
    protected boolean completed = false;
    protected T value = null;
    protected Throwable exception;

    public ConstInput(AsyncProc task) {
        task.super();
    }

    @Override
    public synchronized void onSubscribe(SimpleSubscription subscription) {
        if (closeRequested) {
            subscription.cancel();
        } else {
            this.subscription = subscription;
        }
    }

    public synchronized T current() {
        if (exception != null) {
            throw new IllegalStateException(exception);
        }
        return value;
    }

    public T getValue() {
        return value;
    }

    public Throwable getException() {
        return exception;
    }

    public boolean isDone() {
        return completed || exception != null;
    }

    /**
     * pin bit remains ready
     */
    @Override
    public T next() {
        return current();
    }

    @Override
    public void post(T message) {
        if (message == null) {
            throw new IllegalArgumentException();
        }
        if (isDone()) {
            throw new IllegalStateException("token set already");
        }
        value = message;
        turnOn();
    }

    @Override
    public void postFailure(Throwable throwable) {
        if (isDone()) {
            throw new IllegalStateException("token set already");
        }
        this.exception = throwable;
    }

    @Override
    public void accept(T value, Throwable throwable) {
        if (throwable != null) {
            postFailure(throwable);
        } else {
            post(value);
        }

    }

    public synchronized boolean cancel() {
        if (subscription == null) {
            return cancelled;
        }
        SimpleSubscription subscription = this.subscription;
        this.subscription = null;
        cancelled = true;
        boolean result = subscription.cancel();
        return result;
    }
}
