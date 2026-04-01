-- Comment schema aligned with docs/obsidian-notes/.../comment-spec.md

DROP TABLE IF EXISTS `tb_comment`;
CREATE TABLE `tb_comment` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '所属博客id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '发布者id',
  `content` varchar(500) NOT NULL COMMENT '评论内容',
  `like_count` int(11) NOT NULL DEFAULT 0 COMMENT '点赞数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_comment_blog_id` (`blog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主评论表';

DROP TABLE IF EXISTS `tb_comment_reply`;
CREATE TABLE `tb_comment_reply` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '所属博客id',
  `parent_id` bigint(20) UNSIGNED NOT NULL COMMENT '所属主评论id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '发布者id',
  `reply_to_user_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '被回复用户id',
  `content` varchar(500) NOT NULL COMMENT '回复内容',
  `like_count` int(11) NOT NULL DEFAULT 0 COMMENT '点赞数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_comment_reply_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='副评论表';

