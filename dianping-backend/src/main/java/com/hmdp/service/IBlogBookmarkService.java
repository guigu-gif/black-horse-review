package com.hmdp.service;
import com.hmdp.entity.BlogBookmark;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;

public interface IBlogBookmarkService extends IService<BlogBookmark>{
    Result toggleBookmark(Long blogId);   // 收藏/取消
    Result listMyBookmarks();             // 查询我的收藏列表
}
