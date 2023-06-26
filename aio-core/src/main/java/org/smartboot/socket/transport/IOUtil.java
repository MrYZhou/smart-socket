/*******************************************************************************
 * Copyright (c) 2017-2019, org.smartboot. All rights reserved.
 * project name: smart-socket
 * file name: IOUtil.java
 * Date: 2019-12-31
 * Author: sandao (zhengjunweimail@163.com)
 *
 ******************************************************************************/

package org.smartboot.socket.transport;

import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.NotYetConnectedException;

/**
 * @author 三刀
 * @version V1.0 , 2019/12/2
 */
final class IOUtil {
    /**
     * @param channel 需要被关闭的通道
     */
    public static void close(AsynchronousSocketChannel channel) {
        boolean connected = true;
//        第一个try-catch块尝试关闭通道的输入流，如果通道未连接，
//        则会抛出NotYetConnectedException异常，这时将connected变量设置为false。
        try {
            channel.shutdownInput();
        } catch (IOException ignored) {
        } catch (NotYetConnectedException e) {
            connected = false;
        }
        //第二个try-catch块尝试关闭通道的输出流，如果通道未连接，
        // 则同样会抛出NotYetConnectedException异常，这时不需要再次关闭通道的输出流，因此代码中直接忽略该异常即可。
        try {
            if (connected) {
                channel.shutdownOutput();
            }
        } catch (IOException | NotYetConnectedException ignored) {
        }
        //第三个try-catch块尝试关闭通道本身，如果通道已经关闭，则会抛出IOException异常，这时可以忽略该异常。
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}
