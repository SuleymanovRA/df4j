package org.df4j.core.impl.messagescalar;

import org.df4j.core.impl.AsynchronousCall;
import org.df4j.core.spi.messagescalar.Port;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

public class AsynchronousCallTest {
    @Test
    public void runQuadraticTest() throws InterruptedException, ExecutionException, TimeoutException {
        computeQuadratic(2.0, 6.0, -8.0, -4.0, 1.0);
        computeQuadratic(3.0, 4.0, 1.0, -1.0, -0.3333);
        computeQuadratic(4.0, 2.0, 3.0);
    }

    public void computeQuadratic(double a, double b, double c, double... expectedRoots) throws InterruptedException, TimeoutException, ExecutionException {
        PortFuture<double[]> res = new PortFuture<>();
        QuadraticRoots equation = new QuadraticRoots(res);
        equation.a.post(a);
        equation.b.post(b);
        equation.c.post(c);
        double[] roots = res.get(1, TimeUnit.SECONDS);
        Assert.assertNotNull(roots);
        Assert.assertArrayEquals(expectedRoots, roots, 0.001);
    }

    /**
     * D = sqrt(b^2-4*a*c)
     * roots = (-b +/- D)/2*a
     */
    static class QuadraticRoots {
        // parameters
        PortPromise<Double> a = new PortPromise<>();
        PortPromise<Double> b = new PortPromise<>();
        PortPromise<Double> c = new PortPromise<>();

        public QuadraticRoots(Port<double[]> roots){
            PortPromise<Double> d = new PortPromise<>();
            Discr discr = new Discr(d);
            a.postTo(discr.a);
            b.postTo(discr.b);
            c.postTo(discr.c);
            PortPromise<Double> d2 = new PortPromise<>();
            isNaN cond = new isNaN(roots, d2);
            d.postTo(cond.d);
            Minus minusB = new Minus(new PortPromise<>());
            minusB.a.post(0.0);
            b.postTo(minusB.b);
            Mult aa = new Mult(new PortPromise<>());
            a.postTo(aa.a);
            aa.b.post(2.0);

            Div div1 = new Div(roots);
            Plus plus = new Plus(div1.a);
            ((PortPromise)minusB.out).postTo(plus.a);
            d.postTo(plus.b);
            ((PortPromise)aa.out).postTo(div1.b);

            Div div2 = new Div(roots);
            Minus minus = new Minus(div2.a);
            ((PortPromise)minusB.out).postTo(minus.a);
            d.postTo(minus.b);
            ((PortPromise)aa.out).postTo(div2.b);
        }
    }

    static class isNaN extends AsynchronousCall {
        ConstInput<Double> d = new ConstInput<>();
        Port d1, d2;

        isNaN(Port d1, Port d2){
            this.d1 = d1;
            this.d2 = d2;
        }

        @Override
        public void act() {
            Double d = this.d.get();
            if (Double.isNaN(d)) {
                d1.post(d);
            } else {
                d2.post(d);
            }
        }
    }

    @Test
    public void runDiscrTest() throws InterruptedException, ExecutionException, TimeoutException {
        computeDiscr(3.0, 4.0, 1.0, 2.0);
        computeDiscr(3.0, 2.0, 1.0, Double.NaN);
    }

    public void computeDiscr(double a, double b, double c, double expected) throws InterruptedException, ExecutionException, TimeoutException {
        CompletablePort<Double> res = new CompletablePort<>();
        Discr discr = new Discr(res);
        discr.a.post(a);
        discr.b.post(b);
        discr.c.post(c);
        double result = res.get(1, TimeUnit.SECONDS);
        Assert.assertEquals(expected, result, 0.001);
    }

    @Test
    public void runMultTest() throws InterruptedException, ExecutionException, TimeoutException {
        computeMult(3.0, 4.0, 12.0);
        computeMult(-1.0, -2.0, 2.0);
    }

    public void computeMult(double a, double b, double expected) throws InterruptedException, ExecutionException, TimeoutException {
        CompletablePort<Double> res = new CompletablePort<>();
        Mult mult = new Mult(res);
        mult.a.post(a);
        mult.b.post(b);
        double result = res.get(1, TimeUnit.SECONDS);
        Assert.assertEquals(expected, result, 0.001);
    }

    /**
     * sqrt(b^2-4*d*c)
     */
    static class Discr  {
        // parameters
        AsynchronousCall.ConstInput<Double> a, b, c;

        Discr(Port out){
            Sqrt sqrt = new Sqrt(out);
            Minus minus = new Minus(sqrt.a);
            Square b2 = new Square(minus.a);
            b=b2.a;
            Mult m1 = new Mult(minus.b);
            c = m1.b;
            Mult m2 = new Mult(m1.a);
            m2.a.post(4.0);
            a=m2.b;
        }
    }

    static abstract class BinaryFunc<T> extends AsynchronousCall {
        ConstInput<T> a = new ConstInput<>();
        ConstInput<T> b = new ConstInput<>();
        Port out;

        BinaryFunc(){}

        BinaryFunc(Port out){
            this.out = out;
        }

        public void setOut(Port out) {
            this.out = out;
        }

        @Override
        public void act() {
            T res = compute(a.get(), b.get());
            out.post(res);
        }

        public abstract T compute(T a, T b);
    }

    static abstract class UnaryFunc<T> extends AsynchronousCall {
        ConstInput<T> a = new ConstInput<>();
        Port out;

        UnaryFunc(){}

        UnaryFunc(Port out){
            this.out = out;
        }

        public void setOut(Port out) {
            this.out = out;
        }

        @Override
        public void act() {
            T res = compute(a.get());
            out.post(res);
        }

        public abstract T compute(T a);
    }

    static class Minus extends BinaryFunc<Double> {
        Minus(Port out){
            super(out);
        }

        @Override
        public Double compute(Double a, Double b) {
            return a-b;
        }
    }

    static class Plus extends BinaryFunc<Double> {
        Plus(Port out){
            super(out);
        }

        @Override
        public Double compute(Double a, Double b) {
            return a+b;
        }
    }

    static class Sqrt extends UnaryFunc<Double> {
        Sqrt(Port out){
            super(out);
        }

        @Override
        public Double compute(Double a) {
            return Math.sqrt(a);
        }
    }
    static class Square extends UnaryFunc<Double> {
        Square(){}

        Square(Port out){
            super(out);
        }

        @Override
        public Double compute(Double a) {
            return a*a;
        }
    }

    static class Mult extends BinaryFunc<Double> {
        Mult(Port out){
            super(out);
        }

        @Override
        public Double compute(Double a, Double b) {
            return a*b;
        }
    }

    static class Div extends BinaryFunc<Double> {
        Div(Port out){
            super(out);
        }

        @Override
        public Double compute(Double a, Double b) {
            return a/b;
        }
    }

    public static class CompletablePort<T> extends CompletableFuture<T> implements Port<T> {
        @Override
        public void post(T v) {
            super.complete(v);
        }
    }

}
