package com.alibaba.dubbo.remoting.transport.dispatcher.execution;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.Dispatcher;

/**
 * 只有请求类消息派发到业务线程池处理
 */
public class ExecutionDispatcher implements Dispatcher {

    public static final String NAME = "execution";

    @Override
    public ChannelHandler dispatch(ChannelHandler handler, URL url) {
        return new ExecutionChannelHandler(handler, url);
    }

}
