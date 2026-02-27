package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 线程池（用于异步更新缓存）
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据id查询商铺信息（带缓存，解决缓存穿透和缓存击穿）
     * @param id 商铺id
     * @return 商铺信息
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存穿透解决方案
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从Redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的是否是空值（解决缓存穿透）
        if (shopJson != null) {
            return null;
        }

        // 4. 不存在，根据id查询数据库
        Shop shop = getById(id);

        // 5. 数据库中不存在，将空值写入Redis（解决缓存穿透）
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 存在，写入Redis（添加随机过期时间，避免缓存雪崩）
        long randomTTL = CACHE_SHOP_TTL + new Random().nextInt(5);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), randomTTL, TimeUnit.MINUTES);

        return shop;
    }

    /**
     * 缓存击穿解决方案：互斥锁
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从Redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的是否是空值（解决缓存穿透）
        if (shopJson != null) {
            return null;
        }

        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2 判断是否获取成功
            if (!isLock) {
                // 4.3 失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);  // 递归重试
            }

            // 4.4 成功，根据id查询数据库
            shop = getById(id);

            // 模拟重建的延迟
            Thread.sleep(200);

            // 5. 数据库中不存在，将空值写入Redis
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6. 存在，写入Redis（添加随机过期时间，避免缓存雪崩）
            long randomTTL = CACHE_SHOP_TTL + new Random().nextInt(5);
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), randomTTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 释放互斥锁
            unlock(lockKey);
        }

        return shop;
    }

    /**
     * 获取锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 释放锁
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 缓存击穿解决方案：逻辑过期
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从Redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3. 不存在，直接返回null
            return null;
        }

        // 4. 存在，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((String) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回店铺信息
            return shop;
        }

        // 5.2 已过期，需要缓存重建
        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 6.2 判断是否获取锁成功
        if (isLock) {
            // 6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // 6.4 返回过期的商铺信息
        return shop;
    }

    /**
     * 附近商户查询（Redis GEO）
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 没传经纬度，直接查MySQL
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 有经纬度，尝试从Redis GEO查，失败降级MySQL
        try {
            String key = "shop:geo:" + typeId;
            int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
            int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

            Circle circle = new Circle(new Point(x, y), new Distance(5, Metrics.KILOMETERS));
            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs().includeDistance().sortAscending().limit(end);
            GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                    stringRedisTemplate.opsForGeo().radius(key, circle, args);

            if (results == null || results.getContent().isEmpty()) {
                // GEO无数据，降级MySQL
                Page<Shop> page = query()
                        .eq("type_id", typeId)
                        .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
                return Result.ok(page.getRecords());
            }

            List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
            if (list.size() <= from) {
                return Result.ok(Collections.emptyList());
            }

            List<Long> ids = new ArrayList<>();
            Map<String, Double> distanceMap = new HashMap<>();
            list.stream().skip(from).forEach(r -> {
                String shopId = r.getContent().getName();
                ids.add(Long.valueOf(shopId));
                distanceMap.put(shopId, r.getDistance().getValue());
            });

            String idStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            List<Shop> shops = query()
                    .in("id", ids)
                    .last("ORDER BY FIELD(id," + idStr + ")")
                    .list();
            shops.forEach(shop ->
                    shop.setDistance(distanceMap.get(shop.getId().toString())));
            return Result.ok(shops);

        } catch (Exception e) {
            // Redis不支持GEO命令，降级查MySQL
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
    }

    /**
     * 将商铺数据保存到Redis（带逻辑过期时间）
     * @param id 商铺id
     * @param expireSeconds 逻辑过期时间（秒）
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1. 查询店铺数据
        Shop shop = getById(id);

        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
