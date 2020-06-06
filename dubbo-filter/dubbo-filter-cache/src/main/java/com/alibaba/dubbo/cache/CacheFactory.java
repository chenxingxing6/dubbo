package com.alibaba.dubbo.cache;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.SPI;
import com.alibaba.dubbo.rpc.Invocation;

/**
 * CacheFactory，默认lru
 */
@SPI("lru")
public interface CacheFactory {

    @Adaptive("cache")
    Cache getCache(URL url, Invocation invocation);

}
