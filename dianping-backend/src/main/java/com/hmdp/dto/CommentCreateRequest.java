package com.hmdp.dto;

import lombok.Data;

@Data
public class CommentCreateRequest {
    private Long blogId;
    private String content;
}

