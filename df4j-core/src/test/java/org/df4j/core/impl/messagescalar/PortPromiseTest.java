package org.df4j.core.impl.messagescalar;

import org.junit.Assert;
import org.junit.Test;
import org.junit.Assert.*;

import java.util.concurrent.ExecutionException;

public class PortPromiseTest {

    @Test
    public void portPromiseTest1() throws ExecutionException, InterruptedException {
        PortPromise<Integer> source = new PortPromise<>();
        PortFuture<Integer> sink = new PortFuture<>();
        source.postTo(sink);
        int value = 77;
        source.post(value);
        Assert.assertTrue(sink.isDone());
        Assert.assertEquals(value, sink.get().intValue());
    }

    @Test
    public void portPromiseTest2() throws ExecutionException, InterruptedException {
        int value = 78;
        PortPromise<Integer> source = new PortPromise<>();
        source.post(value);
        PortFuture<Integer> sink = new PortFuture<>();
        source.postTo(sink);
        Assert.assertTrue(sink.isDone());
        Assert.assertEquals(value, sink.get().intValue());
    }
}
