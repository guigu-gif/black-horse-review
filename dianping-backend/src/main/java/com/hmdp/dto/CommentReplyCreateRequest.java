package com.hmdp.dto;

import lombok.Data;

@Data
public class CommentReplyCreateRequest {
    private String content;
    private Long replyToUserId;
}

