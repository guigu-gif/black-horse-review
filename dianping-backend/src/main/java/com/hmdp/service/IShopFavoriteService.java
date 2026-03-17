package com.hmdp.service;

import com.hmdp.entity.ShopFavorite;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;

public interface IShopFavoriteService extends IService<ShopFavorite> {

    Result toggleFavorite(Long shopId);

    Result getFavoriteList();

    Result getFavoriteCount(Long shopId);
}
