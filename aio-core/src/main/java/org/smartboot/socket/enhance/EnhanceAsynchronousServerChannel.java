/*******************************************************************************
 * Copyright (c) 2017-2021, org.smartboot. All rights reserved.
 * project name: smart-socket
 * file name: EnhanceAsynchronousSocketChannel.java
 * Date: 2021-07-29
 * Author: sandao (zhengjunweimail@163.com)
 *
 ******************************************************************************/

package org.smartboot.socket.enhance;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritePendingException;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 模拟JDK7的AIO处理方式
 *
 * @author 三刀
 * @version V1.0 , 2018/5/24
 */
class EnhanceAsynchronousServerChannel extends AsynchronousSocketChannel {
    /**
     * 实际的Socket通道
     */
    protected final SocketChannel channel;
    /**
     * 处理 read 事件的线程资源
     */
    private final EnhanceAsynchronousChannelGroup.Worker readWorker;
    /**
     * 处理 write 事件的线程资源
     */
    protected final EnhanceAsynchronousChannelGroup.Worker commonWorker;

    /**
     * 用于接收 read 通道数据的缓冲区，经解码后腾出缓冲区以供下一批数据的读取
     */
    private ByteBuffer readBuffer;
    /**
     * 存放待输出数据的缓冲区
     */
    private ByteBuffer writeBuffer;

    /**
     * read 回调事件处理器
     */
    private CompletionHandler<Number, Object> readCompletionHandler;
    /**
     * write 回调事件处理器
     */
    private CompletionHandler<Number, Object> writeCompletionHandler;
    /**
     * read 回调事件关联绑定的附件对象
     */
    private Object readAttachment;
    /**
     * write 回调事件关联绑定的附件对象
     */
    private Object writeAttachment;
    private SelectionKey readSelectionKey;
    /**
     * 当前是否正在执行 write 操作
     */
    private boolean writePending;
    /**
     * 当前是否正在执行 read 操作
     */
    private boolean readPending;

    private int writeInvoker;

    private final boolean lowMemory;

    public EnhanceAsynchronousServerChannel(EnhanceAsynchronousChannelGroup group, SocketChannel channel, boolean lowMemory) throws IOException {
        super(group.provider());
        this.channel = channel;
        readWorker = group.getReadWorker();
        commonWorker = group.getCommonWorker();
        this.lowMemory = lowMemory;
    }

    @Override
    public final void close() throws IOException {
        IOException exception = null;
        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            exception = e;
        }
        if (readSelectionKey != null) {
            readSelectionKey.cancel();
            readSelectionKey = null;
        }
        SelectionKey key = channel.keyFor(commonWorker.selector);
        if (key != null) {
            key.cancel();
        }
        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public final AsynchronousSocketChannel bind(SocketAddress local) throws IOException {
        channel.bind(local);
        return this;
    }

    @Override
    public final <T> AsynchronousSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        channel.setOption(name, value);
        return this;
    }

    @Override
    public final <T> T getOption(SocketOption<T> name) throws IOException {
        return channel.getOption(name);
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return channel.supportedOptions();
    }

    @Override
    public final AsynchronousSocketChannel shutdownInput() throws IOException {
        channel.shutdownInput();
        return this;
    }

    @Override
    public final AsynchronousSocketChannel shutdownOutput() throws IOException {
        channel.shutdownOutput();
        return this;
    }

    @Override
    public final SocketAddress getRemoteAddress() throws IOException {
        return channel.getRemoteAddress();
    }

    @Override
    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> connect(SocketAddress remote) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (timeout > 0) {
            throw new UnsupportedOperationException();
        }
        read0(dst, attachment, handler);
    }

    private <V extends Number, A> void read0(ByteBuffer readBuffer, A attachment, CompletionHandler<V, ? super A> handler) {
        if (readPending) {
            throw new ReadPendingException();
        }
        readPending = true;
        this.readBuffer = readBuffer;
        this.readAttachment = attachment;
        this.readCompletionHandler = (CompletionHandler<Number, Object>) handler;
        doRead(handler instanceof FutureCompletionHandler);
    }

    @Override
    public final Future<Integer> read(ByteBuffer readBuffer) {
        FutureCompletionHandler<Integer, Object> readFuture = new FutureCompletionHandler<>();
        read(readBuffer, 0, TimeUnit.MILLISECONDS, null, readFuture);
        return readFuture;
    }

    @Override
    public final <A> void read(ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (timeout > 0) {
            throw new UnsupportedOperationException();
        }
        write0(src, attachment, handler);
    }

    private <V extends Number, A> void write0(ByteBuffer writeBuffer, A attachment, CompletionHandler<V, ? super A> handler) {
        if (writePending) {
            throw new WritePendingException();
        }

        writePending = true;
        this.writeBuffer = writeBuffer;
        this.writeAttachment = attachment;
        this.writeCompletionHandler = (CompletionHandler<Number, Object>) handler;
        doWrite();
    }

    @Override
    public final Future<Integer> write(ByteBuffer src) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final <A> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final SocketAddress getLocalAddress() throws IOException {
        return channel.getLocalAddress();
    }

    public final void doRead(boolean direct) {
        try {
            //此前通过Future调用,且触发了cancel
            if (readCompletionHandler instanceof FutureCompletionHandler && ((FutureCompletionHandler) readCompletionHandler).isDone()) {
                EnhanceAsynchronousChannelGroup.removeOps(readSelectionKey, SelectionKey.OP_READ);
                resetRead();
                return;
            }
            if (lowMemory && direct && readBuffer == null) {
                CompletionHandler<Number, Object> completionHandler = readCompletionHandler;
                Object attach = readAttachment;
                resetRead();
                completionHandler.completed(EnhanceAsynchronousChannelProvider.READABLE_SIGNAL, attach);
                return;
            }
            boolean directRead = direct || (Thread.currentThread() == readWorker.getWorkerThread() && readWorker.invoker++ < EnhanceAsynchronousChannelGroup.MAX_INVOKER);

            long readSize = 0;
            boolean hasRemain = true;
            if (directRead) {
                readSize = channel.read(readBuffer);
                hasRemain = readBuffer.hasRemaining();
            }

            //注册至异步线程
            if (readSize == 0 && readCompletionHandler instanceof FutureCompletionHandler) {
                EnhanceAsynchronousChannelGroup.removeOps(readSelectionKey, SelectionKey.OP_READ);
                commonWorker.addRegister(selector -> {
                    try {
                        channel.register(selector, SelectionKey.OP_READ, EnhanceAsynchronousServerChannel.this);
                    } catch (ClosedChannelException e) {
                        e.printStackTrace();
                        doRead(true);
                    }
                });
                return;
            }
            //释放内存
            if (lowMemory && readSize == 0 && readBuffer.position() == 0) {
                readBuffer = null;
                readCompletionHandler.completed(EnhanceAsynchronousChannelProvider.READ_MONITOR_SIGNAL, readAttachment);
            }

            if (readSize != 0 || !hasRemain) {
                CompletionHandler<Number, Object> completionHandler = readCompletionHandler;
                Object attach = readAttachment;
                resetRead();
                completionHandler.completed((int) readSize, attach);

                if (!readPending && readSelectionKey != null) {
                    EnhanceAsynchronousChannelGroup.removeOps(readSelectionKey, SelectionKey.OP_READ);
                }
            } else if (readSelectionKey == null) {
                readWorker.addRegister(selector -> {
                    try {
                        readSelectionKey = channel.register(selector, SelectionKey.OP_READ, EnhanceAsynchronousServerChannel.this);
                    } catch (ClosedChannelException e) {
                        readCompletionHandler.failed(e, readAttachment);
                    }
                });
            } else {
                EnhanceAsynchronousChannelGroup.interestOps(readWorker, readSelectionKey, SelectionKey.OP_READ);
            }

        } catch (Throwable e) {
            if (readCompletionHandler == null) {
                e.printStackTrace();
                try {
                    close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            } else {
                readCompletionHandler.failed(e, readAttachment);
            }
        }
    }

    private void resetRead() {
        readPending = false;
        readCompletionHandler = null;
        readAttachment = null;
        readBuffer = null;
    }

    public final void doWrite() {
        try {
            int invoker = 0;
            //防止无限递归导致堆栈溢出
            if (commonWorker.getWorkerThread() == Thread.currentThread()) {
                invoker = ++commonWorker.invoker;
            } else if (readWorker.getWorkerThread() != Thread.currentThread()) {
                invoker = ++writeInvoker;
            }
            int writeSize = 0;
            boolean hasRemain = true;
            if (invoker < EnhanceAsynchronousChannelGroup.MAX_INVOKER) {
                writeSize = channel.write(writeBuffer);
                hasRemain = writeBuffer.hasRemaining();
            } else {
                writeInvoker = 0;
            }

            if (writeSize != 0 || !hasRemain) {
                CompletionHandler<Number, Object> completionHandler = writeCompletionHandler;
                Object attach = writeAttachment;
                resetWrite();
                completionHandler.completed(writeSize, attach);
            } else {
                SelectionKey commonSelectionKey = channel.keyFor(commonWorker.selector);
                if (commonSelectionKey == null) {
                    commonWorker.addRegister(selector -> {
                        try {
                            channel.register(selector, SelectionKey.OP_WRITE, EnhanceAsynchronousServerChannel.this);
                        } catch (ClosedChannelException e) {
                            writeCompletionHandler.failed(e, writeAttachment);
                        }
                    });
                } else {
                    EnhanceAsynchronousChannelGroup.interestOps(commonWorker, commonSelectionKey, SelectionKey.OP_WRITE);
                }
            }
        } catch (Throwable e) {
            if (writeCompletionHandler == null) {
                e.printStackTrace();
                try {
                    close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            } else {
                writeCompletionHandler.failed(e, writeAttachment);
            }
        }
    }

    private void resetWrite() {
        writePending = false;
        writeAttachment = null;
        writeCompletionHandler = null;
        writeBuffer = null;
    }

    @Override
    public final boolean isOpen() {
        return channel.isOpen();
    }
}