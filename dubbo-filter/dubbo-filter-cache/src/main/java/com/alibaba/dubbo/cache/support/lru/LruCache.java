package com.alibaba.dubbo.cache.support.lru;

import com.alibaba.dubbo.cache.Cache;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.LRUCache;

import java.util.Map;

/**
 * LruCache:最近最少使用原则删除缓存
 */
public class LruCache implements Cache {

    private final Map<Object, Object> store;

    public LruCache(URL url) {
        final int max = url.getParameter("cache.size", 1000);
        this.store = new LRUCache<Object, Object>(max);
    }

    @Override
    public void put(Object key, Object value) {
        store.put(key, value);
    }

    @Override
    public Object get(Object key) {
        return store.get(key);
    }

}
