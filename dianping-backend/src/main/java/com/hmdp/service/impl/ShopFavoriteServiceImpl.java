package com.hmdp.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopFavorite;
import com.hmdp.mapper.ShopFavoriteMapper;
import com.hmdp.service.IShopFavoriteService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShopFavoriteServiceImpl extends ServiceImpl<ShopFavoriteMapper, ShopFavorite> implements IShopFavoriteService {

    /**
     * 收藏/取消收藏商铺
     * 逻辑：有则删除，无则新增
     */
    @Override
    public Result toggleFavorite(Long shopId) {
        // 1. 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();

        // 2. 查询是否已收藏
        Integer count = query()
                .eq("user_id", userId)
                .eq("shop_id", shopId)
                .count();

        // 3. 判断：有则删除，无则新增
        if (count > 0) {
            // 已收藏 → 取消收藏
            remove(new QueryWrapper<ShopFavorite>()
                    .eq("user_id", userId)
                    .eq("shop_id", shopId));
            return Result.ok("取消收藏成功");
        } else {
            // 未收藏 → 添加收藏
            ShopFavorite favorite = new ShopFavorite();
            favorite.setUserId(userId);
            favorite.setShopId(shopId);
            save(favorite);
            return Result.ok("收藏成功");
        }
    }

    /**
     * 查询当前用户的收藏列表
     * 返回店铺ID列表，按收藏时间倒序
     */
    @Override
    public Result getFavoriteList() {
        // 1. 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();

        // 2. 查询该用户的所有收藏记录
        List<ShopFavorite> favorites = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .list();

        // 3. 提取店铺ID列表
        if (favorites.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> shopIds = favorites.stream()
                .map(ShopFavorite::getShopId)
                .collect(Collectors.toList());

        // 4. 返回店铺ID列表（后续可扩展：根据ID查询完整店铺信息）
        return Result.ok(shopIds);
    }

    //查询店铺被收藏数量
    public Result getFavoriteCount(Long shopId) {
        Integer count = query()
                .eq("shop_id",shopId)
                .count();
        return Result.ok(count);
    }


}
