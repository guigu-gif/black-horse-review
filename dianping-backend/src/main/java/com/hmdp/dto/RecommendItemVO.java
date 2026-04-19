package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class RecommendItemVO {
    private Long id;
    private String name;
    /** score/10，保留一位小数，例如 "4.8" */
    private String score;
    private Long avgPrice;
    private String address;
    private String openStatus;
    /** 从shop字段推导的标签，例如 ["美食", "辣", "¥30-50"] */
    private List<String> tags;
    /** AI生成的推荐理由 */
    private String reason;
}
