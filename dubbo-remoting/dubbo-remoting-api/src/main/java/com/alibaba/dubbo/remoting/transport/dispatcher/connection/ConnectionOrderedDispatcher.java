package com.alibaba.dubbo.remoting.transport.dispatcher.connection;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.Dispatcher;

/**
 * connect disconnect ensure the order
 * 多生产->单消费（实现了链接建立->链接断开事件处理变为顺序化）
 */
public class ConnectionOrderedDispatcher implements Dispatcher {

    public static final String NAME = "connection";

    @Override
    public ChannelHandler dispatch(ChannelHandler handler, URL url) {
        return new ConnectionOrderedChannelHandler(handler, url);
    }

}
