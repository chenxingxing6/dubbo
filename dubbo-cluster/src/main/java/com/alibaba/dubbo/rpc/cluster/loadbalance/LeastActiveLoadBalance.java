package com.alibaba.dubbo.rpc.cluster.loadbalance;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.Random;

/**
 * 最少活跃数（dubbo框架自定义了一个Filter，用于计算服务被调用的次数）
 *
 * 实现原理：
 * 1.先找最小活跃的服务并统计权重和出现的频次
 * 2.最小活跃次数出现一次（直接使用它）
 * 3.出现多次&权重不同（按权重数随机）
 * 4.出现多次&权重相同（随机调用）
 *
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "leastactive";
    private final Random random = new Random();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int length = invokers.size(); // Number of invokers
        // 最少活跃数
        int leastActive = -1;
        int leastCount = 0; // The number of invokers having the same least active value (leastActive)
        int[] leastIndexs = new int[length]; // The index of invokers having the same least active value (leastActive)
        int totalWeight = 0; // The sum of with warmup weights
        int firstWeight = 0; // Initial value, used for comparision
        boolean sameWeight = true; // Every invoker has the same weight value?
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            // 活跃数
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive(); // Active number
            // 权重
            int afterWarmup = getWeight(invoker, invocation); // Weight
            if (leastActive == -1 || active < leastActive) { // Restart, when find a invoker having smaller least active value.
                leastActive = active; // Record the current least active value
                leastCount = 1; // Reset leastCount, count again based on current leastCount
                leastIndexs[0] = i; // Reset
                totalWeight = afterWarmup; // Reset
                firstWeight = afterWarmup; // Record the weight the first invoker
                sameWeight = true; // Reset, every invoker has the same weight value?
            } else if (active == leastActive) { // If current invoker's active value equals with leaseActive, then accumulating.
                leastIndexs[leastCount++] = i; // Record index number of this invoker
                totalWeight += afterWarmup; // Add this invoker's weight to totalWeight.
                // If every invoker has the same weight?
                if (sameWeight && i > 0 && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // 最少活跃数服务为1
        if (leastCount == 1) {
            return invokers.get(leastIndexs[0]);
        }
        // 权重不同，权重随机
        if (!sameWeight && totalWeight > 0) {
            int offsetWeight = random.nextInt(totalWeight) + 1;
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexs[i];
                offsetWeight -= getWeight(invokers.get(leastIndex), invocation);
                if (offsetWeight <= 0)
                    return invokers.get(leastIndex);
            }
        }
        // 权重相同，随机获取
        return invokers.get(leastIndexs[random.nextInt(leastCount)]);
    }
}
