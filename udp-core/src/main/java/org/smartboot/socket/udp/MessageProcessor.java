/*
 * Copyright (c) 2017, org.smartboot. All rights reserved.
 * project name: smart-socket
 * file name: MessageProcessor.java
 * Date: 2017-11-25
 * Author: sandao
 */

package org.smartboot.socket.udp;

import java.net.SocketAddress;

/**
 * 消息处理器。
 *
 * <p>
 * 通过实现该接口，对完成解码的消息进行业务处理。
 * </p>
 * <h2>示例：</h2>
 * <p>
 * <pre>
 * public class IntegerServerProcessor implements MessageProcessor<Integer> {
 *     public void process(AioSession<Integer> session, Integer msg) {
 *         Integer respMsg = msg + 1;
 *         System.out.println("receive data from client: " + msg + " ,rsp:" + (respMsg));
 *         try {
 *             session.write(respMsg);
 *         } catch (IOException e) {
 *             e.printStackTrace();
 *         }
 *     }
 *
 *     public void stateEvent(AioSession<Integer> session, StateMachineEnum stateMachineEnum, Throwable throwable) {
 *         switch (stateMachineEnum) {
 *             case NEW_SESSION:
 *                 ...
 *                 break;
 *             case SESSION_CLOSED:
 *                 ...
 *                 break;
 *             case PROCESS_EXCEPTION:
 *                 ...
 *                 break;
 *             default:
 *                 ...
 *         }
 *     }
 * }
 *     </pre>
 * </p>
 *
 * @author 三刀
 * @version V1.0.0 2019/8/18
 */
public interface MessageProcessor<T> {

    /**
     * 处理接收到的消息
     *
     * @param channel 通信会话
     * @param msg     待处理的业务消息
     */
    void process(UdpChannel<T> channel, SocketAddress remote, T msg);

}
