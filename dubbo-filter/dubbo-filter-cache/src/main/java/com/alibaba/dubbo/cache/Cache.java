package com.alibaba.dubbo.cache;

/**
 * dubbo缓存实现
 */
public interface Cache {

    void put(Object key, Object value);

    Object get(Object key);

}
