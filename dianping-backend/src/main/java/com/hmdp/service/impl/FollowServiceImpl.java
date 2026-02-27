package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private BlogMapper blogMapper;

    /**
     * 关注 / 取关
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;

        if (isFollow) {
            // 关注：MySQL插入 + Redis SADD
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            // 补推：把对方已有的历史博客写入我的 Feed 收件箱
            String feedKey = "feed:" + userId;
            List<Blog> blogs = blogMapper.selectList(
                    new QueryWrapper<Blog>().eq("user_id", followUserId));
            for (Blog blog : blogs) {
                long score = blog.getCreateTime() != null
                        ? blog.getCreateTime().toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
                        : System.currentTimeMillis();
                stringRedisTemplate.opsForZSet().add(feedKey, blog.getId().toString(), score);
            }
        } else {
            // 取关：MySQL删除 + Redis SREM
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }

    /**
     * 查询当前用户是否关注了某人
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count > 0);
    }

    /**
     * 共同关注（Redis Set交集）
     */
    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String myKey = "follow:" + userId;
        String otherKey = "follow:" + id;

        // SINTER：求两个Set的交集
        Set<String> commonIds = stringRedisTemplate.opsForSet().intersect(myKey, otherKey);
        if (commonIds == null || commonIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 根据交集ID查询用户信息返回
        List<Long> ids = commonIds.stream().map(Long::parseLong).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }
}
