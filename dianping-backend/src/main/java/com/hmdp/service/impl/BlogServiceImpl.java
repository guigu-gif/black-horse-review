package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 保存博客，并推送到所有粉丝的 Feed 收件箱（ZSet）
     */
    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean isSave = save(blog);
        if (!isSave) {
            return Result.fail("新增笔记失败");
        }
        // 查出当前用户的所有粉丝
        List<Follow> follows = followService.query()
                .eq("follow_user_id", user.getId()).list();
        // 把博客ID推送到每个粉丝的收件箱（score = 当前时间戳）
        for (Follow follow : follows) {
            String key = "feed:" + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    /**
     * 滚动分页查询 Feed 流（关注的人发的博客）
     * 直接从 MySQL 查询，无需 ZSet 预填充，历史博客也能查到
     * max：上次最后一条的时间戳（epoch millis），第一次传 Date.now()
     * offset：上次结果中 create_time == minTime 的条数，用于翻页时跳过已读
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 当前用户的收件箱 key
        Long userId = UserHolder.getUser().getId();
        String key = "feed:" + userId;

        // 2. 从ZSet取博客ID，按score倒序，游标翻页
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                stringRedisTemplate.opsForZSet()
                        .reverseRangeByScoreWithScores(key, 0, max, offset, 5);

        if (typedTuples == null || typedTuples.isEmpty()) {
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setMinTime(0L);
            empty.setOffset(0);
            return Result.ok(empty);
        }

        // 3. 解析：提取blogId列表 + 算出下次游标（最小score + 相同score的条数）
        List<Long> ids = new ArrayList<>();
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }

        // 4. 按ID查博客，用FIELD保持顺序
        String idStr = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        List<Blog> blogs = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();

        // 5. 填充作者和点赞状态
        blogs.forEach(blog -> {
            User u = userService.getById(blog.getUserId());
            blog.setName(u.getNickName());
            blog.setIcon(u.getIcon());
            setIsLike(blog);
        });

        // 6. 返回
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(os);
        return Result.ok(result);
    }

    /**
     * 点赞 / 取消点赞（Toggle）
     */
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;

        // 判断当前用户是否已点赞
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());

        if (Boolean.TRUE.equals(isLiked)) {
            // 已点赞 → 取消：移出 Redis Set + 数据库 liked-1
            stringRedisTemplate.opsForSet().remove(key, userId.toString());
            update().setSql("liked = liked - 1").eq("id", id).update();
        } else {
            // 未点赞 → 点赞：加入 Redis Set + 数据库 liked+1
            stringRedisTemplate.opsForSet().add(key, userId.toString());
            update().setSql("liked = liked + 1").eq("id", id).update();
        }
        return Result.ok();
    }

    /**
     * 查询热门 blog 列表，并标记当前用户是否点赞
     */
    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();

        records.forEach(blog -> {
            // 填充作者信息
            User user = userService.getById(blog.getUserId());
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            // 标记当前用户是否点赞过
            setIsLike(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询单篇 blog 详情
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        setIsLike(blog);
        return Result.ok(blog);
    }

    /**
     * 按笔记标题多关键词搜索，按相关度（匹配词数）从高到低排序
     * 空格分词，任意词命中即返回，命中词数越多越靠前
     */
    @Override
    public Result searchByTitle(String keyword, Integer current) {
        if (StrUtil.isBlank(keyword)) return Result.ok(Collections.emptyList());

        String[] tokens = keyword.trim().split("\\s+");

        // WHERE: title LIKE '%k1%' OR title LIKE '%k2%' ...
        QueryWrapper<Blog> wrapper = new QueryWrapper<>();
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i].replace("'", "");
            if (i == 0) wrapper.like("title", t);
            else wrapper.or().like("title", t);
        }

        // ORDER BY 相关度（匹配词数）DESC，再按点赞数 DESC
        StringBuilder rel = new StringBuilder();
        for (String token : tokens) {
            String t = token.replace("'", "");
            if (rel.length() > 0) rel.append("+");
            rel.append("(CASE WHEN title LIKE '%").append(t).append("%' THEN 1 ELSE 0 END)");
        }
        wrapper.last("ORDER BY (" + rel + ") DESC, liked DESC");

        Page<Blog> page = page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), wrapper);
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            User user = userService.getById(blog.getUserId());
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            setIsLike(blog);
        });
        return Result.ok(records);
    }

    /**
     * 按博主搜索：ID精确匹配 或 昵称多关键词模糊匹配，结果按点赞数排序
     */
    @Override
    public Result searchByUser(String keyword, Integer current) {
        if (StrUtil.isBlank(keyword)) return Result.ok(Collections.emptyList());

        String[] tokens = keyword.trim().split("\\s+");
        List<Long> userIds = new ArrayList<>();

        // 纯数字：先尝试 ID 精确查
        if (keyword.matches("\\d+")) {
            User u = userService.getById(Long.parseLong(keyword));
            if (u != null) userIds.add(u.getId());
        }

        // 每个关键词都做昵称模糊查，结果去重合并
        for (String token : tokens) {
            userService.query().like("nick_name", token).list()
                    .forEach(u -> { if (!userIds.contains(u.getId())) userIds.add(u.getId()); });
        }

        if (userIds.isEmpty()) return Result.ok(Collections.emptyList());

        Page<Blog> page = query()
                .in("user_id", userIds)
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            User user = userService.getById(blog.getUserId());
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            setIsLike(blog);
        });
        return Result.ok(records);
    }

    /**
     * 设置 blog 的 isLike 字段（当前用户是否点过赞）
     */
    private void setIsLike(Blog blog) {
        // 未登录时不需要查
        if (UserHolder.getUser() == null) {
            return;
        }
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        blog.setIsLike(Boolean.TRUE.equals(isLiked));
    }
}
