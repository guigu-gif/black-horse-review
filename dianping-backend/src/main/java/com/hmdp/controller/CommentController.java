package com.hmdp.controller;

import com.hmdp.dto.CommentCreateRequest;
import com.hmdp.dto.CommentReplyCreateRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.ICommentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
public class CommentController {

    @Resource
    private ICommentService commentService;

    @GetMapping("/blog/{blogId}/comments")
    public ResponseEntity<Result> queryComments(@PathVariable("blogId") Long blogId,
                                @RequestParam(value = "page", defaultValue = "1") Integer page,
                                @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return toResponse(commentService.queryComments(blogId, page, size));
    }

    @GetMapping("/comment/{commentId}/replies")
    public ResponseEntity<Result> queryReplies(@PathVariable("commentId") Long commentId) {
        return toResponse(commentService.queryReplies(commentId));
    }

    @PostMapping("/comment")
    public ResponseEntity<Result> createComment(@RequestBody CommentCreateRequest request) {
        return toResponse(commentService.createComment(request));
    }

    @PostMapping("/comment/{commentId}/reply")
    public ResponseEntity<Result> createReply(@PathVariable("commentId") Long commentId,
                              @RequestBody CommentReplyCreateRequest request) {
        return toResponse(commentService.createReply(commentId, request));
    }

    @DeleteMapping("/comment/{commentId}")
    public ResponseEntity<Result> deleteComment(@PathVariable("commentId") Long commentId) {
        return toResponse(commentService.deleteComment(commentId));
    }

    @PutMapping("/comment/{commentId}/like")
    public ResponseEntity<Result> likeComment(@PathVariable("commentId") Long commentId) {
        return toResponse(commentService.likeComment(commentId));
    }

    @PutMapping("/comment/reply/{replyId}/like")
    public ResponseEntity<Result> likeReply(@PathVariable("replyId") Long replyId) {
        return toResponse(commentService.likeReply(replyId));
    }

    private ResponseEntity<Result> toResponse(Result result) {
        int status = result.getCode() != null ? result.getCode() : 200;
        return ResponseEntity.status(status).body(result);
    }
}

