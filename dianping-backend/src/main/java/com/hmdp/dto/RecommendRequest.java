package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class RecommendRequest {
    private String sessionId;
    private String supplement;
    private List<String> excludeTags;
    private List<Long> excludedShopIds;
    private Integer hour;
    private Boolean restart;
}
