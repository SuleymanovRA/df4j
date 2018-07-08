package org.df4j.core.node.messagescalar;

import org.df4j.core.connector.messagescalar.ConstInput;
import org.df4j.core.node.Action;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class AsyncBiFunction<U, V, R> extends AsyncResultFuture<R> {
    public final ConstInput<U> arg1 = new ConstInput<>(this);
    public final ConstInput<V> arg2 = new ConstInput<>(this);
    protected final BiFunction<? super U,? super V,? extends R> fn;

    public AsyncBiFunction(BiFunction<? super U, ? super V, ? extends R> fn) {
        this.fn = fn;
    }

    public AsyncBiFunction(BiConsumer<? super U, ? super V> action) {
        this.fn = (a1, a2)->{action.accept(a1, a2); return null;};
    }

    public AsyncBiFunction(Runnable action) {
        this.fn = (a1, a2)->{action.run(); return null;};
    }

    public void runFunction(U arg1, V arg2) {
        try {
            R res = fn.apply(arg1, arg2);
            complete(res);
        } catch (Throwable e) {
            completeExceptionally(e);
        }
    }

}
