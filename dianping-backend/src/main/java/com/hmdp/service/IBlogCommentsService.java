package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogCommentsService extends IService<BlogComments> {

    Result queryComments(Long blogId);

    Result saveComment(Long blogId, String content);

    Result deleteComment(Long commentId);
}
