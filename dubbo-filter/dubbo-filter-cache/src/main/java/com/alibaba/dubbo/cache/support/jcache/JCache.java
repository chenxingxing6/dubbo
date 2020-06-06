package com.alibaba.dubbo.cache.support.jcache;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.spi.CachingProvider;
import java.util.concurrent.TimeUnit;

/**
 * JCache：用于缓存的Java API，提供一组通用的接口和类，可用于将java对象临时存储在内存中
 */
public class JCache implements com.alibaba.dubbo.cache.Cache {

    private final Cache<Object, Object> store;

    public JCache(URL url) {
        String method = url.getParameter(Constants.METHOD_KEY, "");
        String key = url.getAddress() + "." + url.getServiceKey() + "." + method;
        // jcache parameter is the full-qualified class name of SPI implementation
        String type = url.getParameter("jcache");

        CachingProvider provider = type == null || type.length() == 0 ? Caching.getCachingProvider() : Caching.getCachingProvider(type);
        CacheManager cacheManager = provider.getCacheManager();
        Cache<Object, Object> cache = cacheManager.getCache(key);
        if (cache == null) {
            try {
                //configure the cache
                MutableConfiguration config =
                        new MutableConfiguration<Object, Object>()
                                .setTypes(Object.class, Object.class)
                                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS, url.getMethodParameter(method, "cache.write.expire", 60 * 1000))))
                                .setStoreByValue(false)
                                .setManagementEnabled(true)
                                .setStatisticsEnabled(true);
                cache = cacheManager.createCache(key, config);
            } catch (CacheException e) {
                // concurrent cache initialization
                cache = cacheManager.getCache(key);
            }
        }

        this.store = cache;
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
