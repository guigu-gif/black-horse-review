package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.CommentCreateRequest;
import com.hmdp.dto.CommentReplyCreateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.Comment;

public interface ICommentService extends IService<Comment> {

    Result queryComments(Long blogId, Integer page, Integer size);

    Result queryReplies(Long commentId);

    Result createComment(CommentCreateRequest request);

    Result createReply(Long commentId, CommentReplyCreateRequest request);

    Result deleteComment(Long commentId);

    Result likeComment(Long commentId);

    Result likeReply(Long replyId);
}

