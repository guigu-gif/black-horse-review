package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一道：刷新拦截器，拦所有路径，order(0)先跑
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // 第二道：登录拦截器，只拦需要登录的路径，order(1)后跑
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .addPathPatterns(
                        "/user/me",
                        "/user/logout",
                        "/voucher-order/**",
                        "/blog/save",
                        "/blog/like/**",
                        "/blog-comments/**",
                        "/follow/**",
                        "/upload/**"
                )
                .order(1);
    }
}
