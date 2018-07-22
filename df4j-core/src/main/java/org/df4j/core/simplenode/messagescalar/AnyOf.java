package org.df4j.core.simplenode.messagescalar;

import org.df4j.core.boundconnector.messagescalar.AsyncResult;
import org.df4j.core.boundconnector.messagescalar.ScalarSubscriber;

public class AnyOf<T> extends CompletablePromise<T> {
    Enter subscriber = new Enter();

    public AnyOf() {
    }

    public AnyOf(AsyncResult<? extends T>... sources) {
        for (AsyncResult source: sources) {
            source.subscribe(subscriber);
        }
    }

    class Enter implements ScalarSubscriber<T> {
        @Override
        public void post(T value) {
            synchronized (AnyOf.this) {
                if (!isDone()) {
                    complete(value);
                }
            }
        }

        @Override
        public void postFailure(Throwable ex) {
            synchronized (AnyOf.this) {
                if (!isDone()) {
                    completeExceptionally(ex);
                }
            }
        }
    }

}
