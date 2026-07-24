package com.lld.im.service.message.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author: Chackylee
 * @description:
 **/
@Data
@TableName("im_message_history")
public class ImMessageHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer appId;

    private String fromId;

    private String toId;

    private String ownerId;

    /** messageBodyId */
    private Long messageKey;
    /** 序列号*/
    private Long sequence;

    private String messageRandom;

    private Long messageTime;

    private Long createTime;

}
