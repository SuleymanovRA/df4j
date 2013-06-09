/*
 * Copyright 2011 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
//package com.github.rfqu.df4j.util;
package com.github.rfqu.df4j.ext;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.github.rfqu.df4j.core.DataflowNode;
import com.github.rfqu.df4j.core.Callback;
import com.github.rfqu.df4j.core.ListenableFuture;
import com.github.rfqu.df4j.core.Promise;

/**
 * abstract node with multiple inputs, single output and exception handling
 * Unlike Actor, it is single shot. 
 * @param <R> type of result
 */
public abstract class Function<R> extends DataflowNode
    implements Promise<R>, Future<R>
{
//    protected final Demand<R> res=new Demand<R>();
    ListenableFuture<R> res=new ListenableFuture<R>();
    public Function(Executor executor) {
        super(executor);
    }

    public Function() {
    }

    /**
     * Subscribes a consumer to which the result will be send.
     * Function evaluation would not start until at least one
     * consumer subscribes.
     * It is allowed to subscribe after the function is evaluated.
     * @param sink
     * @return 
     */
    @Override
    public Function<R> addListener(Callback<R> sink) {
        res.addListener(sink);
        return this;
    }
    
    //========= backend
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return res.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return res.isCancelled();
    }

    @Override
    public boolean isDone() {
        return res.isDone();
    }

    @Override
    public R get() throws InterruptedException, ExecutionException {
        return res.get();
    }

    @Override
    public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return res.get(timeout, unit);
    }

    /**
     * evaluates the function's result
     */
    abstract protected R eval();
    
    protected void act() {
        res.post(eval());
    }

    protected void handleException(Throwable exc) {
        res.postFailure(exc);
    }
    
    //========== inner classes
    
    /** Scalar Input which also redirects failures 
     */
    public class CallbackInput<T> extends Input<T> implements Callback<T> {
        @Override
        public void postFailure(Throwable exc) {
            Function.this.postFailure(exc);
        }
    }
        
   /**
     * Unary operation
    *
    * @param <T> type of the operand and the result
    */
   public static abstract class UnaryOp<T> extends Function<T> implements Callback<T> {
       protected CallbackInput<T> input=new CallbackInput<T>();

       @Override
       public void post(T value) {
           input.post(value);
       }

       @Override
       protected T eval() {
           return eval(input.get());
       }

       abstract protected T eval(T operand);

   }
   
   /**
    * Binary operation: classic dataflow object.
    * Waits for both operands to arrive,
    * computes the operation, and sends result to the Demand object,
    * which routes the result to the interested parties.
    *
    * @param <T> the type of operands and the result
    */
    public static abstract class BinaryOp<T> extends Function<T> {
        CallbackInput<T> p1 = new CallbackInput<T>();
        CallbackInput<T> p2 = new CallbackInput<T>();

        @Override
        protected T eval() {
            return eval(p1.get(), p2.get());
        }

        abstract protected T eval(T opnd, T opnd2);

    }

}