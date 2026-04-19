package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class RecommendResponse {
    private String sessionId;
    private Boolean finished;
    private String message;
    private List<RecommendItemVO> items;
}
