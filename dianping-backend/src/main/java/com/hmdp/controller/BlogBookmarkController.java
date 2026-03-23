package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IBlogBookmarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blog/bookmark")
public class BlogBookmarkController {
    @Autowired
    private IBlogBookmarkService bookmarkService;

    @PostMapping("/{blogId}")
    public Result toggleBookmark(@PathVariable Long blogId) {
        return bookmarkService.toggleBookmark(blogId);
    }

    @GetMapping("/list")
    public Result listMyBookmarks() {
        return bookmarkService.listMyBookmarks();
    }
}
