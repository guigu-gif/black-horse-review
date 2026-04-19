package com.hmdp.utils;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 店铺布隆过滤器
 * 作用：防缓存穿透第一道防线，用于快速判断 shopId 一定不存在
 * 特性：说"不在" → 100%不在；说"在" → 可能在也可能不在（误判率1%）
 */
@Component
public class ShopBloomFilter {

    @Resource
    private ShopMapper shopMapper;

    private BloomFilter<Long> bloomFilter;

    /**
     * 应用启动时初始化：把数据库中所有店铺ID塞入过滤器
     */
    @PostConstruct
    public void init() {
        List<Long> shopIds = shopMapper.selectList(null)
                .stream()
                .map(Shop::getId)
                .collect(Collectors.toList());

        // 预期容量取实际数量的2倍（留增长空间），最小10000
        int expectedSize = Math.max(shopIds.size() * 2, 10000);
        bloomFilter = BloomFilter.create(Funnels.longFunnel(), expectedSize, 0.01);

        shopIds.forEach(bloomFilter::put);
    }

    /**
     * 判断 id 是否可能存在
     * 返回 false → 一定不存在，直接拦截，不查Redis不查DB
     * 返回 true  → 可能存在，继续走正常查询流程
     */
    public boolean mightContain(Long id) {
        return bloomFilter.mightContain(id);
    }

    /**
     * 新增店铺时同步更新过滤器
     */
    public void add(Long id) {
        bloomFilter.put(id);
    }
}
