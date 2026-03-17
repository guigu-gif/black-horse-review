package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IShopFavoriteService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 商铺收藏 Controller
 */
@RestController
@RequestMapping("/shop/favorite")
public class ShopFavoriteController {

    @Resource
    private IShopFavoriteService shopFavoriteService;

    /**
     * 收藏/取消收藏商铺
     * POST /shop/favorite/{shopId}
     */
    @PostMapping("/{shopId}")
    public Result toggleFavorite(@PathVariable("shopId") Long shopId) {
        return shopFavoriteService.toggleFavorite(shopId);
    }

    /**
     * 查询当前用户的收藏列表
     * GET /shop/favorite/list
     */
    @GetMapping("/list")
    public Result getFavoriteList() {
        return shopFavoriteService.getFavoriteList();
    }

    /**
     * 查询店铺被收藏的数量
     * GET /shop/favorite/count/{shopId}
     */
    @GetMapping("/count/{shopId}")
    public Result getFavoriteCount(@PathVariable("shopId") Long shopId) {
        return shopFavoriteService.getFavoriteCount(shopId);
    }

}
