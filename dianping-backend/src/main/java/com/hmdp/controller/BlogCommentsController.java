package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    @GetMapping("/{blogId}")
    public Result queryComments(@PathVariable("blogId") Long blogId) {
        return blogCommentsService.queryComments(blogId);
    }

    @PostMapping("/{blogId}")
    public Result saveComment(@PathVariable("blogId") Long blogId,
                              @RequestBody Map<String, String> body) {
        return blogCommentsService.saveComment(blogId, body.get("content"));
    }

    @DeleteMapping("/{commentId}")
    public Result deleteComment(@PathVariable("commentId") Long commentId) {
        return blogCommentsService.deleteComment(commentId);
    }
}
