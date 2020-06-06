package com.alibaba.dubbo.cache.support.threadlocal;

import com.alibaba.dubbo.cache.Cache;
import com.alibaba.dubbo.common.URL;

import java.util.HashMap;
import java.util.Map;

/**
 * ThreadLocalCache
 */
public class ThreadLocalCache implements Cache {

    private final ThreadLocal<Map<Object, Object>> store;

    public ThreadLocalCache(URL url) {
        this.store = new ThreadLocal<Map<Object, Object>>() {
            @Override
            protected Map<Object, Object> initialValue() {
                return new HashMap<Object, Object>();
            }
        };
    }

    @Override
    public void put(Object key, Object value) {
        store.get().put(key, value);
    }

    @Override
    public Object get(Object key) {
        return store.get().get(key);
    }

}
