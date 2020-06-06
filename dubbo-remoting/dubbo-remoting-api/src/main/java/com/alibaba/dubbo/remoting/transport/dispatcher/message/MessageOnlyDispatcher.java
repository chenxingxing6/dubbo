package com.alibaba.dubbo.remoting.transport.dispatcher.message;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.Dispatcher;

/**
 * 请求响应交给业务线程处理
 * 连接事件，断开，心跳等直接在I/O上执行（耗时较短）
 */
public class MessageOnlyDispatcher implements Dispatcher {

    public static final String NAME = "message";

    @Override
    public ChannelHandler dispatch(ChannelHandler handler, URL url) {
        return new MessageOnlyChannelHandler(handler, url);
    }

}
