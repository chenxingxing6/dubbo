package com.alibaba.dubbo.rpc.cluster.loadbalance;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;

import java.util.List;
import java.util.Random;

/**
 * 随机
 * 思路：如果服务多实例权重相同，则进行随机调用；如果权重不同，按照总权重取随机数
 *
 * 根据总权重数生成一个随机数，然后和具体服务实例的权重进行相减做偏移量，然后找出
 * 偏移量小于0的，比如随机数为10，某一个服务实例的权重为12，那么10-12=-2<0成立，
 * 则该服务被调用，这种策略在随机的情况下尽可能保证权重大的服务会被随机调用。
 */
public class RandomLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "random";
    private final Random random = new Random();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // 总个数
        int length = invokers.size();
        int totalWeight = 0;
        boolean sameWeight = true;
        for (int i = 0; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            totalWeight += weight; // Sum
            if (sameWeight && i > 0 && weight != getWeight(invokers.get(i - 1), invocation)) {
                sameWeight = false;
            }
        }
        // 加权
        if (totalWeight > 0 && !sameWeight) {
            // 总权重随机数，并确定随机落在那个片上
            int offset = random.nextInt(totalWeight);
            for (int i = 0; i < length; i++) {
                offset -= getWeight(invokers.get(i), invocation);
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // 随机获取
        return invokers.get(random.nextInt(length));
    }

}
