package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogService extends IService<Blog> {

    Result saveBlog(Blog blog);

    Result likeBlog(Long id);

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result queryBlogOfFollow(Long max, Integer offset);

    Result searchByTitle(String keyword, Integer current);

    Result searchByUser(String keyword, Integer current);
}
