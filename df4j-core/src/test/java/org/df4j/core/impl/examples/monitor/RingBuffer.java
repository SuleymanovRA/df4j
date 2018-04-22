package org.df4j.core.impl.examples.monitor;

import org.df4j.core.spi.messagescalar.Port;

class RingBuffer<R> extends Monitor<RingBuffer<R>> {
    int capacity;
    Object[] buf;
    int posR=0;
    int count=0;
    
    public RingBuffer (int capacity) {
        this.capacity=capacity;
        buf=new Object[capacity];
    }
    
    class Put implements Runnable {
        R element;

        public Put(R element) {
            this.element = element;
        }

        @Override
        public void run() {
            if (count==capacity) {
                doWait();
            } else {
                buf[(posR+count)%capacity]=element;
                count++;
                doNotifyAll();
            }
        }
        
    }

    class Get implements Runnable {
        Port<R> consumer;
        
        public Get(Port<R> consumer) {
            this.consumer = consumer;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            if (count==0) {
                doWait();
            } else {
                consumer.post((R) buf[posR]);
                count--;
                posR=(posR+1)%capacity;
                doNotifyAll();
            }
        }
        
    }
}