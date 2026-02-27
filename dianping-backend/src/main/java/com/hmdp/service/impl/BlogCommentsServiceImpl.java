package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Override
    public Result queryComments(Long blogId) {
        // 查询一级评论（parentId = 0），按时间正序
        List<BlogComments> comments = query()
                .eq("blog_id", blogId)
                .eq("parent_id", 0)
                .eq("status", false)
                .orderByAsc("create_time")
                .list();

        if (comments.isEmpty()) {
            return Result.ok(comments);
        }

        // 批量查询评论用户信息
        List<Long> userIds = comments.stream().map(BlogComments::getUserId).collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = comments.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("userId", c.getUserId());
            map.put("content", c.getContent());
            map.put("createTime", c.getCreateTime());
            map.put("liked", c.getLiked());
            User u = userMap.get(c.getUserId());
            if (u != null) {
                map.put("nickName", u.getNickName());
                map.put("icon", u.getIcon());
            }
            return map;
        }).collect(Collectors.toList());

        return Result.ok(result);
    }

    @Override
    public Result deleteComment(Long commentId) {
        Long userId = UserHolder.getUser().getId();

        // 查询这条评论是否存在且属于当前用户
        BlogComments comment = getById(commentId);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            return Result.fail("无权删除他人评论");
        }

        // 删除评论
        removeById(commentId);

        // 同步更新 blog 的评论数量 -1（不低于0）
        blogService.update()
                .setSql("comments = comments - 1")
                .eq("id", comment.getBlogId())
                .gt("comments", 0)
                .update();

        return Result.ok();
    }

    @Override
    public Result saveComment(Long blogId, String content) {
        if (content == null || content.trim().isEmpty()) {
            return Result.fail("评论内容不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        BlogComments comment = new BlogComments();
        comment.setBlogId(blogId);
        comment.setUserId(userId);
        comment.setContent(content.trim());
        comment.setParentId(0L);
        comment.setAnswerId(0L);
        comment.setLiked(0);
        comment.setStatus(false);
        save(comment);
        // 同步更新 blog 的评论数量
        blogService.update().setSql("comments = comments + 1").eq("id", blogId).update();
        return Result.ok();
    }
}
