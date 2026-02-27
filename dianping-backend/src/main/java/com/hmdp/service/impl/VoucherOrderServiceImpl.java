package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 加载Lua脚本（静态，程序启动时只加载一次）
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 内存阻塞队列：存放"已判断通过、待写库"的订单
    // 容量1024*1024，约100万条，足够缓冲
    private final BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024 * 1024);

    // 后台消费者线程池（单线程，顺序消费队列）
    private static final ExecutorService ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // Spring容器启动完成后，立即启动后台消费者线程
    @PostConstruct
    private void init() {
        ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 后台消费者线程：不断从队列取订单，写入数据库
     */
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // take()：队列为空时阻塞等待，不占CPU
                    VoucherOrder order = orderQueue.take();
                    handleVoucherOrder(order);
                } catch (InterruptedException e) {
                    log.error("订单处理线程被中断", e);
                }
            }
        }
    }

    /**
     * 真正写数据库的方法（在后台线程里执行，用户不需要等）
     */
    @Transactional
    public void handleVoucherOrder(VoucherOrder order) {
        // Lua脚本已经在Redis层面保证了一人一单和库存，这里直接写库即可
        // 扣减MySQL库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足（MySQL），orderId={}", order.getId());
            return;
        }
        // 写订单
        save(order);
    }

    /**
     * 查询我的订单（关联优惠券标题）
     */
    @Override
    public Result queryMyOrders() {
        Long userId = UserHolder.getUser().getId();

        List<VoucherOrder> orders = query().eq("user_id", userId)
                .orderByDesc("create_time").list();

        if (orders.isEmpty()) {
            return Result.ok(orders);
        }

        List<Long> voucherIds = orders.stream()
                .map(VoucherOrder::getVoucherId).collect(Collectors.toList());
        Map<Long, Voucher> voucherMap = voucherService.listByIds(voucherIds).stream()
                .collect(Collectors.toMap(Voucher::getId, v -> v));

        Map<Long, LocalDateTime> expireMap = seckillVoucherService.listByIds(voucherIds).stream()
                .collect(Collectors.toMap(sv -> sv.getVoucherId(), sv -> sv.getEndTime()));

        List<Map<String, Object>> result = orders.stream().map(o -> {
            Voucher v = voucherMap.get(o.getVoucherId());
            Map<String, Object> map = new HashMap<>();
            map.put("id", o.getId());
            map.put("voucherId", o.getVoucherId());
            map.put("title", v != null ? v.getTitle() : "未知券");
            map.put("status", o.getStatus());
            map.put("createTime", o.getCreateTime());
            map.put("expireTime", expireMap.get(o.getVoucherId()));
            return map;
        }).collect(Collectors.toList());

        return Result.ok(result);
    }

    /**
     * 普通券领取（无库存限制，一人一张，直接生成核销码）
     */
    @Override
    public Result claimVoucher(Long voucherId) {
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || voucher.getStatus() == null || voucher.getStatus() != 1) {
            return Result.fail("优惠券不可用");
        }
        if (Integer.valueOf(1).equals(voucher.getType())) {
            return Result.fail("秒杀券请通过秒杀接口领取");
        }

        Long userId = UserHolder.getUser().getId();
        RLock lock = redissonClient.getLock("lock:claim:" + userId + ":" + voucherId);
        boolean isLock = lock.tryLock();
        if (!isLock) return Result.fail("操作频繁，请稍后再试");

        try {
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) return Result.fail("每人限领一张");

            VoucherOrder order = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            order.setId(orderId);
            order.setUserId(userId);
            order.setVoucherId(voucherId);
            order.setStatus(2);
            save(order);

            return Result.ok(orderId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 商家核销券码
     */
    @Override
    public Result verifyOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null) return Result.fail("核销码无效");
        if (order.getStatus() == 3) return Result.fail("该券已核销，请勿重复使用");
        if (order.getStatus() != 2) return Result.fail("订单状态异常，无法核销");

        boolean ok = update()
                .set("status", 3)
                .set("use_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", 2)
                .update();

        if (!ok) return Result.fail("核销失败，请重试");

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("voucherId", order.getVoucherId());
        return Result.ok(data);
    }

    /**
     * 取消订单：status=1 → status=4
     */
    @Override
    public Result cancelOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = getById(orderId);
        if (order == null) return Result.fail("订单不存在");
        if (!order.getUserId().equals(userId)) return Result.fail("无权操作");
        if (order.getStatus() != 1) return Result.fail("只有待支付订单可以取消");

        boolean ok = update()
                .set("status", 4)
                .eq("id", orderId)
                .eq("status", 1)
                .update();
        return ok ? Result.ok() : Result.fail("取消失败，请重试");
    }

    /**
     * 模拟支付：status=1 → status=2
     */
    @Override
    public Result payOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = getById(orderId);
        if (order == null) return Result.fail("订单不存在");
        if (!order.getUserId().equals(userId)) return Result.fail("无权操作");
        if (order.getStatus() != 1) return Result.fail("订单状态不支持支付");

        boolean ok = update()
                .set("status", 2)
                .eq("id", orderId)
                .eq("status", 1)
                .update();
        return ok ? Result.ok(orderId) : Result.fail("支付失败，请重试");
    }

    /**
     * 秒杀优惠券（异步版：Lua原子判断 + 阻塞队列异步写库）
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1. 执行Lua脚本：原子判断库存+一人一单，通过则扣Redis库存
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(
                        "seckill:stock:" + voucherId,   // KEYS[1] 库存key
                        "seckill:order:" + voucherId    // KEYS[2] 已购用户set
                ),
                userId.toString()                        // ARGV[1] 用户ID
        );

        // 2. 根据Lua返回值判断结果
        int r = result.intValue();
        if (r == 1) return Result.fail("库存不足！");
        if (r == 2) return Result.fail("每人限购一张！");

        // 3. Lua返回0：判断通过，生成订单ID，塞进阻塞队列（不写MySQL，立即返回）
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(1); // 待支付
        orderQueue.add(order); // 交给后台线程写库

        // 4. 立即返回订单ID（此时MySQL还没写，但Redis已保证抢购资格）
        return Result.ok(orderId);
    }
}
