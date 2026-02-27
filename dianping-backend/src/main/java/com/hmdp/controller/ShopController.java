package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.EmbeddingService;
import com.hmdp.service.impl.QdrantService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private QdrantService qdrantService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.updateById(shop);
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return shopService.queryShopByType(typeId, current, x, y);
    }

    /**
     * 分页查询商铺信息（前端首页使用）
     * @param current 页码
     * @param pageSize 每页大小
     * @return 商铺列表
     */
    @GetMapping("/list")
    public Result queryShopList(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "12") Integer pageSize
    ) {
        // 分页查询
        Page<Shop> page = shopService.query()
                .page(new Page<>(current, pageSize));
        // 返回分页数据（包含 records）

        return Result.ok(page);
    }

    /**
     * 语义搜索商铺（AI 向量搜索）
     * 例如：/shop/search?q=适合约会的餐厅
     */
    @GetMapping("/search")
    public Result searchShop(@RequestParam("q") String query) {
        // 1. 把搜索词转成向量
        List<Float> vector = embeddingService.embed(query);
        // 2. 在 Qdrant 里找最相似的商铺 ID
        List<Long> shopIds = qdrantService.search(vector, 30);
        if (shopIds.isEmpty()) {
            return Result.ok(shopIds);
        }
        // 3. 根据 ID 从数据库查完整商铺信息
        String idStr = shopIds.stream().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("");
        List<Shop> shops = shopService.query()
                .in("id", shopIds)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();
        return Result.ok(shops);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
