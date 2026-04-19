package com.hmdp.controller;

import com.hmdp.dto.RecommendRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IRecommendService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Resource
    private IRecommendService recommendService;

    @PostMapping("/init")
    public Result init(@RequestBody RecommendRequest request) {
        return recommendService.recommend(request);
    }
}
