package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器：只检查ThreadLocal里有没有用户
 * 用户信息由RefreshTokenInterceptor负责放入，这里只做验证
 */
public class LoginInterceptor implements HandlerInterceptor {

    // RefreshTokenInterceptor已经处理了Redis操作，这里不需要
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        // 保留构造参数，MvcConfig传入方式不变
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // ThreadLocal里没有用户 → 未登录 → 401
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
