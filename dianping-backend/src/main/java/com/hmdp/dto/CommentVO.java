package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long id;
    private Long blogId;
    private Long parentId;
    private Long userId;
    private Long replyToUserId;
    private String replyToNickName;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
    private Boolean isLiked;
    private String nickName;
    private String icon;
}
