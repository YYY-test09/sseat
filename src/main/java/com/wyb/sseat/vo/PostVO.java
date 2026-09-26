package com.wyb.sseat.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 帖子视图：补充发帖人昵称头像 */
@Data
public class PostVO {
    private Long id;
    private Long userId;
    private String authorName;   // 发帖人昵称
    private String authorAvatar;
    private String title;
    private String type;
    private String coverImg;
    private String content;
    private Integer status;
    private String rejectReason;
    private Integer viewCount;
    private Integer likeCount;
    private LocalDateTime createTime;

    /** 列表摘要：正文前 80 字 */
    public String getSummary() {
        if (content == null) return "";
        String s = content.replaceAll("\\s+", " ").trim();
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
