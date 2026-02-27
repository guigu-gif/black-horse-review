package com.hmdp;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.EmbeddingService;
import com.hmdp.service.impl.QdrantService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private QdrantService qdrantService;

    /**
     * 把所有店铺的经纬度写入Redis GEO（只需跑一次）
     */
    @Test
    void loadShopGeoData() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            String key = "shop:geo:" + entry.getKey();
            for (Shop shop : entry.getValue()) {
                stringRedisTemplate.opsForGeo()
                        .add(key, new Point(shop.getX(), shop.getY()),
                                shop.getId().toString());
            }
        }
        System.out.println("GEO数据加载完成");
    }

    /**
     * 重置 Qdrant 集合（维度变了或数据有问题时用）
     * 执行后需要重新跑 loadShopVectors()
     */
    @Test
    void resetShopCollection() {
        qdrantService.deleteCollection();
        qdrantService.initCollection();
        System.out.println("集合已重建，请重新跑 loadShopVectors()");
    }

    /**
     * 把所有商铺文字描述转成向量，存入 Qdrant（只需跑一次）
     * 商铺文字 = 名称 + 商圈 + 地址，让 AI 能理解"这家店是干什么的"
     */
    @Test
    void loadShopVectors() {
        qdrantService.initCollection();
        List<Shop> shops = shopService.list();
        for (Shop shop : shops) {
            // 拼接商铺描述文字
            String text = shop.getName()
                    + (shop.getArea() != null ? " " + shop.getArea() : "")
                    + (shop.getAddress() != null ? " " + shop.getAddress() : "");
            try {
                List<Float> vector = embeddingService.embed(text);
                qdrantService.upsert(shop.getId(), vector, shop.getName());
                System.out.println("已写入: " + shop.getName());
            } catch (Exception e) {
                System.out.println("跳过: " + shop.getName() + " 原因: " + e.getMessage());
            }
        }
        System.out.println("向量数据加载完成，共 " + shops.size() + " 家商铺");
    }

    /**
     * 测试秒杀功能（单线程）
     */
    @Test
    void testSeckill() {
        // 模拟用户登录（用户ID = 1）
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);

        // 秒杀优惠券（ID = 10）
        Result result = voucherOrderService.seckillVoucher(10L);
        System.out.println("秒杀结果：" + result);
    }

    /**
     * 测试并发秒杀（模拟 100 个线程同时抢购）
     */
    @Test
    void testConcurrentSeckill() throws InterruptedException {
        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(100);
        // 计数器（等待所有线程执行完）
        CountDownLatch latch = new CountDownLatch(100);

        // 模拟 100 个用户同时抢购
        for (int i = 1; i <= 100; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    // 模拟用户登录
                    UserDTO user = new UserDTO();
                    user.setId(userId);
                    UserHolder.saveUser(user);

                    // 秒杀
                    Result result = voucherOrderService.seckillVoucher(10L);
                    System.out.println("用户 " + userId + " 秒杀结果：" + result.getSuccess());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程执行完
        latch.await();
        executor.shutdown();
        System.out.println("测试完成！");
    }

    /**
     * 测试一人多单（同一个用户多次抢购）
     */
    @Test
    void testOnePersonMultiOrder() {
        // 模拟用户登录（用户ID = 1）
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);

        // 第一次抢购
        Result result1 = voucherOrderService.seckillVoucher(10L);
        System.out.println("第一次抢购：" + result1);

        // 第二次抢购（应该失败）
        Result result2 = voucherOrderService.seckillVoucher(10L);
        System.out.println("第二次抢购：" + result2);
    }
}
