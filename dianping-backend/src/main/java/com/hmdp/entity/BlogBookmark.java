package com.hmdp.entity;

import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;


@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_bookmark")
public class BlogBookmark implements Serializable{

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long blogId;
    private LocalDateTime createTime;
}
