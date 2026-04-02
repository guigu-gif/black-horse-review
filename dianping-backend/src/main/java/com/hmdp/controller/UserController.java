package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IBlogBookmarkService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.ICommentService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IShopFavoriteService;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.hmdp.utils.UserHolder;
import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private IFollowService followService;

    @Resource
    private IBlogService blogService;

    @Resource
    private ICommentService commentService;

    @Resource
    private IShopFavoriteService shopFavoriteService;

    @Resource
    private IBlogBookmarkService blogBookmarkService;

    /**
     * 发送手机验证码（固定114514）
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result logout(){
        UserHolder.removeUser();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) return Result.ok();
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            info = new UserInfo();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        info.setFollowee(followService.query().eq("user_id", userId).count());
        info.setFans(followService.query().eq("follow_user_id", userId).count());
        info.setBlogCount(blogService.query().eq("user_id", userId).count());
        info.setCommentCount(commentService.query().eq("user_id", userId).count());
        int shopCollect = shopFavoriteService.query().eq("user_id", userId).count();
        int blogCollect = blogBookmarkService.query().eq("user_id", userId).count();
        info.setCollectCount(shopCollect + blogCollect);
        return Result.ok(info);
    }
}
