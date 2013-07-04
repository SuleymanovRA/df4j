package com.github.rfqu.df4j.nio;

import java.io.IOException;
import java.net.SocketAddress;

import com.github.rfqu.df4j.core.Callback;
import com.github.rfqu.df4j.nio.AsyncChannelFactory;
import com.github.rfqu.df4j.nio.AsyncServerSocketChannel;
import com.github.rfqu.df4j.nio.AsyncSocketChannel;

public class AsyncChannelFactory1 extends AsyncChannelFactory {
    @Override
    public AsyncServerSocketChannel newAsyncServerSocketChannel() throws IOException
    {
        return new AsyncServerSocketChannel1();
    }

    @Override
    public AsyncSocketChannel newAsyncSocketChannel(SocketAddress addr) throws IOException {
        return new AsyncSocketChannel1(addr);
    }

}