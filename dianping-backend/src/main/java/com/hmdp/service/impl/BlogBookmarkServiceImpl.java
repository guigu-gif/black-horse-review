package com.hmdp.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogBookmark;
import com.hmdp.mapper.BlogBookmarkMapper;
import com.hmdp.service.IBlogBookmarkService;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BlogBookmarkServiceImpl extends
        ServiceImpl<BlogBookmarkMapper, BlogBookmark>
        implements IBlogBookmarkService {

        @Autowired
        private IBlogService blogService;



    @Override
    public Result toggleBookmark(Long blogId) {
        Long userId = UserHolder.getUser().getId();

        int count = query()
                .eq("user_id", userId)
                .eq("blog_id", blogId)
                .count();

        if (count > 0) {
            // 已收藏，删掉
            remove(new QueryWrapper<BlogBookmark>()
                    .eq("user_id", userId)
                    .eq("blog_id", blogId));
        } else {
            // 未收藏，新增
            BlogBookmark bookmark = new BlogBookmark();
            bookmark.setUserId(userId);
            bookmark.setBlogId(blogId);
            save(bookmark);
        }

        return Result.ok();
    }

    @Override
    public Result listMyBookmarks() {
        // 待实现
        Long userId = UserHolder.getUser().getId();

        List<BlogBookmark> bookmarks = query()
                .eq("user_id", userId)
                .list();

        List<Long> blogIds = bookmarks.stream()
                .map(BlogBookmark::getBlogId)
                .collect(Collectors.toList());
        if (blogIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Blog> blogs = blogService.listByIds(blogIds);
        return Result.ok(blogs);
    }

}
