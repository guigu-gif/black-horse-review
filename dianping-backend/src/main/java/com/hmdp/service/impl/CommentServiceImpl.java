package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.CommentCreateRequest;
import com.hmdp.dto.CommentReplyCreateRequest;
import com.hmdp.dto.CommentVO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Comment;
import com.hmdp.entity.CommentReply;
import com.hmdp.entity.User;
import com.hmdp.mapper.CommentMapper;
import com.hmdp.mapper.CommentReplyMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.ICommentService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    @Resource
    private CommentReplyMapper commentReplyMapper;
    @Resource
    private IUserService userService;
    @Resource
    private IBlogService blogService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryComments(Long blogId, Integer page, Integer size) {
        if (blogId == null) {
            return Result.fail("blogId不能为空");
        }
        int current = page == null || page < 1 ? DEFAULT_PAGE : page;
        int pageSize = size == null || size < 1 ? DEFAULT_SIZE : size;
        Page<Comment> pager = query()
                .eq("blog_id", blogId)
                .orderByDesc("like_count")
                .orderByDesc("create_time")
                .page(new Page<>(current, pageSize));
        List<CommentVO> vos = buildMainCommentVO(pager.getRecords());
        return Result.ok(vos, pager.getTotal());
    }

    @Override
    public Result queryReplies(Long commentId) {
        if (commentId == null) {
            return Result.fail("commentId不能为空");
        }
        Comment parent = getById(commentId);
        if (parent == null) {
            return Result.fail("评论不存在");
        }
        List<CommentReply> replies = commentReplyMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CommentReply>()
                        .eq("parent_id", commentId)
                        .orderByAsc("create_time")
        );
        List<CommentVO> vos = buildReplyCommentVO(replies);
        return Result.ok(vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createComment(CommentCreateRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (request == null || request.getBlogId() == null) {
            return Result.fail("blogId不能为空");
        }
        Result contentValidation = validateContent(request.getContent());
        if (!contentValidation.getSuccess()) {
            return contentValidation;
        }
        Blog blog = blogService.getById(request.getBlogId());
        if (blog == null) {
            return Result.fail(404, "博客不存在");
        }

        Comment comment = new Comment();
        comment.setBlogId(request.getBlogId());
        comment.setUserId(user.getId());
        comment.setContent(request.getContent().trim());
        comment.setLikeCount(0);
        comment.setCreateTime(LocalDateTime.now());
        save(comment);

        updateBlogCommentCount(comment.getBlogId(), 1);
        return Result.ok(toMainVO(comment, getUserMap(Collections.singletonList(user.getId()))));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createReply(Long commentId, CommentReplyCreateRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (commentId == null) {
            return Result.fail("commentId不能为空");
        }
        Result contentValidation = validateContent(request == null ? null : request.getContent());
        if (!contentValidation.getSuccess()) {
            return contentValidation;
        }

        Comment parent = getById(commentId);
        if (parent == null) {
            return Result.fail(404, "评论不存在");
        }

        CommentReply reply = new CommentReply();
        reply.setBlogId(parent.getBlogId());
        reply.setParentId(commentId);
        reply.setUserId(user.getId());
        reply.setReplyToUserId(request.getReplyToUserId());
        reply.setContent(request.getContent().trim());
        reply.setLikeCount(0);
        reply.setCreateTime(LocalDateTime.now());
        commentReplyMapper.insert(reply);

        updateBlogCommentCount(parent.getBlogId(), 1);
        return Result.ok(toReplyVO(reply, getUserMap(Collections.singletonList(user.getId()))));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteComment(Long commentId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (commentId == null) {
            return Result.fail("commentId不能为空");
        }
        Comment main = getById(commentId);
        if (main == null) {
            return Result.fail(404, "评论不存在");
        }
        if (!Objects.equals(main.getUserId(), user.getId())) {
            return Result.fail(403, "无权操作");
        }
        List<CommentReply> replies = commentReplyMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CommentReply>()
                        .eq("parent_id", commentId)
        );
        removeById(commentId);
        commentReplyMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CommentReply>()
                .eq("parent_id", commentId));
        deleteLikedKey(commentId);
        replies.forEach(reply -> deleteLikedKey(reply.getId()));
        updateBlogCommentCount(main.getBlogId(), -(1 + replies.size()));
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteReply(Long replyId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (replyId == null) {
            return Result.fail("replyId不能为空");
        }
        CommentReply reply = commentReplyMapper.selectById(replyId);
        if (reply == null) {
            return Result.fail(404, "评论不存在");
        }
        if (!Objects.equals(reply.getUserId(), user.getId())) {
            return Result.fail(403, "无权操作");
        }
        commentReplyMapper.deleteById(replyId);
        deleteLikedKey(replyId);
        updateBlogCommentCount(reply.getBlogId(), -1);
        return Result.ok();
    }

    @Override
    public Result likeComment(Long commentId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Comment comment = getById(commentId);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        return toggleLike(commentId, user.getId(),
                () -> update().setSql("like_count = like_count + 1").eq("id", commentId).update(),
                () -> update().setSql("like_count = IF(like_count > 0, like_count - 1, 0)").eq("id", commentId).update());
    }

    @Override
    public Result likeReply(Long replyId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        CommentReply reply = commentReplyMapper.selectById(replyId);
        if (reply == null) {
            return Result.fail("评论不存在");
        }
        return toggleLike(replyId, user.getId(),
                () -> {
                    com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CommentReply> wrapper =
                            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                    wrapper.setSql("like_count = like_count + 1").eq("id", replyId);
                    return commentReplyMapper.update(null, wrapper) > 0;
                },
                () -> {
                    com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CommentReply> wrapper =
                            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                    wrapper.setSql("like_count = IF(like_count > 0, like_count - 1, 0)").eq("id", replyId);
                    return commentReplyMapper.update(null, wrapper) > 0;
                });
    }

    private Result toggleLike(Long commentId, Long userId, SupplierWithResult inc, SupplierWithResult dec) {
        String key = RedisConstants.COMMENT_LIKED_KEY + commentId;
        String member = userId.toString();
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, member);
        boolean updated;
        if (Boolean.TRUE.equals(isLiked)) {
            updated = dec.get();
            if (updated) {
                stringRedisTemplate.opsForSet().remove(key, member);
            }
        } else {
            updated = inc.get();
            if (updated) {
                stringRedisTemplate.opsForSet().add(key, member);
            }
        }
        if (!updated) {
            return Result.fail("操作失败，请重试");
        }
        return Result.ok();
    }

    private Result validateContent(String content) {
        if (StrUtil.isBlank(content)) {
            return Result.fail("评论内容不能为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            return Result.fail("评论内容不能超过500字");
        }
        return Result.ok();
    }

    private List<CommentVO> buildMainCommentVO(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = comments.stream().map(Comment::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = getUserMap(userIds);
        return comments.stream().map(c -> toMainVO(c, userMap)).collect(Collectors.toList());
    }

    private List<CommentVO> buildReplyCommentVO(List<CommentReply> replies) {
        if (replies == null || replies.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = replies.stream()
                .flatMap(r -> {
                    List<Long> ids = new ArrayList<>(2);
                    ids.add(r.getUserId());
                    if (r.getReplyToUserId() != null) {
                        ids.add(r.getReplyToUserId());
                    }
                    return ids.stream();
                })
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = getUserMap(userIds);
        return replies.stream().map(r -> toReplyVO(r, userMap)).collect(Collectors.toList());
    }

    private CommentVO toMainVO(Comment comment, Map<Long, User> userMap) {
        CommentVO vo = BeanUtil.copyProperties(comment, CommentVO.class);
        vo.setParentId(null);
        vo.setReplyToUserId(null);
        vo.setIsLiked(isLiked(comment.getId()));
        fillUser(userMap, vo, comment.getUserId());
        return vo;
    }

    private CommentVO toReplyVO(CommentReply reply, Map<Long, User> userMap) {
        CommentVO vo = BeanUtil.copyProperties(reply, CommentVO.class);
        vo.setIsLiked(isLiked(reply.getId()));
        fillUser(userMap, vo, reply.getUserId());
        if (reply.getReplyToUserId() != null) {
            User replyTo = userMap.get(reply.getReplyToUserId());
            if (replyTo != null) {
                vo.setReplyToNickName(replyTo.getNickName());
            }
        }
        return vo;
    }

    private void fillUser(Map<Long, User> userMap, CommentVO vo, Long userId) {
        User user = userMap.get(userId);
        if (user == null) {
            return;
        }
        vo.setNickName(user.getNickName());
        vo.setIcon(user.getIcon());
    }

    private Map<Long, User> getUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private boolean isLiked(Long commentId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return false;
        }
        String key = RedisConstants.COMMENT_LIKED_KEY + commentId;
        Boolean liked = stringRedisTemplate.opsForSet().isMember(key, user.getId().toString());
        return Boolean.TRUE.equals(liked);
    }

    private void deleteLikedKey(Long commentId) {
        stringRedisTemplate.delete(RedisConstants.COMMENT_LIKED_KEY + commentId);
    }

    private void updateBlogCommentCount(Long blogId, int delta) {
        if (blogId == null || delta == 0) {
            return;
        }
        if (delta > 0) {
            blogService.update().setSql("comments = comments + " + delta).eq("id", blogId).update();
            return;
        }
        int abs = Math.abs(delta);
        blogService.update()
                .setSql("comments = IF(comments >= " + abs + ", comments - " + abs + ", 0)")
                .eq("id", blogId)
                .update();
    }

    @FunctionalInterface
    private interface SupplierWithResult {
        boolean get();
    }
}
