package com.hmdp.service;

import com.hmdp.dto.RecommendRequest;
import com.hmdp.dto.Result;

public interface IRecommendService {
    Result recommend(RecommendRequest request);
}
