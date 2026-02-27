package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.*;


import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @GetMapping("list")
    public Result queryMyOrders() {
        return voucherOrderService.queryMyOrders();
    }

    /**
     * 普通券领取
     */
    @PostMapping("/claim/{voucherId}")
    public Result claimVoucher(@PathVariable Long voucherId) {
        return voucherOrderService.claimVoucher(voucherId);
    }

    /**
     * 模拟支付
     */
    @PostMapping("/pay/{orderId}")
    public Result payOrder(@PathVariable Long orderId) {
        return voucherOrderService.payOrder(orderId);
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel/{orderId}")
    public Result cancelOrder(@PathVariable Long orderId) {
        return voucherOrderService.cancelOrder(orderId);
    }

    /**
     * 商家核销券码（orderId 即核销码）
     */
    @PostMapping("/verify/{orderId}")
    public Result verifyOrder(@PathVariable Long orderId) {
        return voucherOrderService.verifyOrder(orderId);
    }
}
