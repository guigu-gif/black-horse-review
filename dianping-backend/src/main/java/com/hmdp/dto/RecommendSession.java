package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class RecommendSession {
    private String sessionId;
    private Long userId;
    private String supplement;
    private Integer hour;
    private List<String> excludeTags;
    private List<Long> excludedShopIds;
    private List<Long> candidateShopIds;
    private List<Long> shownShopIds;
    private int candidateOffset; // 已从数据库取出的总数，预加载时作为 OFFSET 起点
}
