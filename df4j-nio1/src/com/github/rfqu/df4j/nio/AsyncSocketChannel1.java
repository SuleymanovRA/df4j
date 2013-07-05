/*
 * Copyright 2011-2012 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.github.rfqu.df4j.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.github.rfqu.df4j.core.Callback;
import com.github.rfqu.df4j.core.CompletableFuture;
import com.github.rfqu.df4j.core.ListenableFuture;
import com.github.rfqu.df4j.core.Task;
import com.github.rfqu.df4j.nio.AsyncSocketChannel;
import com.github.rfqu.df4j.nio.SocketIORequest;

/**
 * Asynchronously executes I/O socket requests using {@link java.nio.channels.Selector}.
 * Simplifies input-output, handling queues of I/O requests.
 * 
 * Internally, manages 2 actors: one for reading requests and one for writing requests.
 * After request is served, it is sent to the port denoted by <code>replyTo</code> parameter in
 * the read/write methods.
 */
public class AsyncSocketChannel1 extends AsyncSocketChannel {
    protected final CompletableFuture<AsyncSocketChannel> connEvent=new CompletableFuture<AsyncSocketChannel>();
    protected final CompletableFuture<AsyncSocketChannel> closeEvent=new CompletableFuture<AsyncSocketChannel>();
	private SelectorThread selectorThread = SelectorThread
			.getCurrentSelectorThread();
	protected volatile SocketChannel socketChannel;
	private final SelectorListener selectorListener=new SelectorListener();
	
    {
        reader = new ReaderQueue();
        writer = new WriterQueue();
    }
   
    /**
	 * for server-side socket
	 */ 
	public AsyncSocketChannel1() {
    }

    void init(SocketChannel channel) throws IOException {
        channel.configureBlocking(false);
	    channel.socket().setTcpNoDelay(true);
	    socketChannel = channel;
		reader.resume();
		writer.resume();
		connEvent.post(this);
	}

	/**
	 * for client-side socket
	 * Starts connection to a server. IO requests can be queued immediately,
	 * but will be executed only after connection completes.
	 * If interested in the moment when connection is established, add a
	 * listener to the {@link connEvent}.
	 * @return 
	 * 
	 * @throws IOException
	 */
	public void connect(final SocketAddress addr) throws IOException {
		// Create a non-blocking socket channel
		final SocketChannel channel = SocketChannel.open();
		channel.configureBlocking(false);
		selectorThread.execute(new Task(){
            @Override
            public void run() {
                boolean connected;
                try {
                    connected = channel.connect(addr);
                    if (connected) {
                        init(channel);
                    } else {
                        // Kick off connection establishment
                        selectorThread.registerNow(channel, SelectionKey.OP_CONNECT,
                                selectorListener);
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
         });
	}

	public <R extends Callback<AsyncSocketChannel>> R addConnListener(R listener) {
		connEvent.addListener(listener);
		return listener;
	}

    public ListenableFuture<AsyncSocketChannel> getConnEvent() {
        return connEvent;
    }

    @Override
    public ListenableFuture<AsyncSocketChannel> getCloseEvent() {
        return closeEvent;
    }

    // ================== StreamPort I/O interface

	/**
	 * disallows subsequent posts of requests; already posted requests would be
	 * processed.
	 */
	@Override
    public synchronized void close() throws IOException {
        if (isClosed()) return;
        closeEvent.post(this);
		if (socketChannel==null) { // this may happen if connection rejected
		    return;
		}
        try {
            socketChannel.close();
        } finally {
            socketChannel=null;
        }
	}

	// ===================== inner classes

    void interestOn(int op) throws ClosedChannelException {
        selectorThread.registerNow(socketChannel, op, selectorListener);
    }

    void interestOff(int op) throws ClosedChannelException {
        selectorThread.interestOff(socketChannel, op);
    }

    abstract class RequestQueue1 extends RequestQueue {
        
	    public RequestQueue1(boolean isReader) {
            super(selectorThread, isReader);
        }

        /** is on when resources from network are available */
	    Lockup net=new Lockup();

        public void resume() {
            net.on();
            try {
                interestOff(getSelectionKeyOp());
            } catch (ClosedChannelException e) {
            }
        }

        @Override
        public synchronized void post(SocketIORequest<?> request) {
            if (!request.getBuffer().hasRemaining()) {
                request.postFailure(new IllegalArgumentException());
                return;
            }
            super.post(request);
        }
        
        /** called when a request could not be executed immediately */
        void interestOn() throws ClosedChannelException {
            net.off(); // do not react on next request
            AsyncSocketChannel1.this.interestOn(getSelectionKeyOp());  // until selector notifies
        }

        abstract int getSelectionKeyOp(); 
 	}

	class ReaderQueue extends RequestQueue1 {
        public ReaderQueue() {
            super(true);
        }

        int getSelectionKeyOp() { 
            return SelectionKey.OP_READ;
        }
	    /**
	     * @param request
	     * @throws ClosedChannelException 
	     */
		@Override
        public void act(SocketIORequest<?> request) {
            ByteBuffer readBuffer = request.getBuffer();
            // Attempt to read off the channel
            int numRead;
            try {
                numRead = socketChannel.read(readBuffer);
            } catch (IOException exc) {
                request.postFailure(exc);
                close(); // what TODO with the remaining requests?
                return;
            }
            if (numRead == -1) {
                // Remote entity shut the socket down cleanly. Do the
                // same from our end and cancel the channel.
                request.post(-1); // TODO define how to signal end of stream
                close(); // what TODO with the remaining requests?
            } else if (!request.getBuffer().hasRemaining()) {
                // buffer is full;
                request.post(numRead);
            } else if (numRead == 0) {
                // no data available, put request back into queue,
                // and order to wait next signal from selector,
                // then repeat attempt to read to the same buffer
                try {
                    interestOn();
                    pushback();
                } catch (ClosedChannelException e) {
                    request.postFailure(e);
                    close(); // what TODO with the remaining requests?
                }
            } else {
                request.post(numRead);
            }
		}
	}

	class WriterQueue extends RequestQueue1 {
	    public WriterQueue() {
            super(false);
        }

        int getSelectionKeyOp() { 
	        return SelectionKey.OP_WRITE;
	    }

		@Override
		protected void act(SocketIORequest<?> request) {
            ByteBuffer writeBuffer = request.getBuffer();
            int numWrit;
            try {
                numWrit = socketChannel.write(writeBuffer);
            } catch (IOException exc) {
                request.postFailure(exc);
                close();
                return;
            }
            if (numWrit == -1) {
                // Remote entity shut the socket down cleanly. Do the
                // same from our end and cancel the channel.
                request.post(-1); // TODO define how to signal end of stream
                close();
            } else if (!request.getBuffer().hasRemaining()) {
                // all data from buffer written to socket
                request.post(numWrit);
            } else {
                // not all data from buffer written
                // put request back into queue,
                // and order to wait next signal from selector,
                // then repeat attempt to read to the same buffer
                try {
                    interestOn();
                    pushback();
                } catch (ClosedChannelException e) {
                    request.postFailure(e);
                    close(); // what TODO with the remaining requests?
                }
            }
		}
	}
	
	class SelectorListener implements SelectorEventListener {
	    /** called on selector thread
	     * 
	     */
	    @Override
	    public void onSelectorEvent(SelectionKey key) {
	        if (key.isConnectable()) {
	            try {
	                SocketChannel channel = (SocketChannel) key.channel();
	                channel.finishConnect();
	                selectorThread.interestOff(channel, SelectionKey.OP_CONNECT);
	                init(channel);
	            } catch (IOException e) {
	                e.printStackTrace();
	                try {
                        AsyncSocketChannel1.this.close();
                    } catch (IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    } // TODO send failure
	            }
	        }
	        if (key.isValid()&& key.isReadable()) {
	            reader.resume();
	        }
	        if (key.isValid()&& key.isWritable()) {
	            writer.resume();
	        }
	     }
	}
}
