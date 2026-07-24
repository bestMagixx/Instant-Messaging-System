package com.lld.im.message.dao;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author: Chackylee
 * @description:
 **/
@Data
@TableName("im_message_body")
public class ImMessageBodyEntity {

    private Integer appId;

    /** messageBodyId - 主键 */
    @TableId
    private Long messageKey;

    /** messageBody*/
    private String messageBody;

    /** 密钥，用于对messageBody进行加密和解密  */
    private String securityKey;


    private Long messageTime;

    private Long createTime;

    private String extra;

    private Integer delFlag;

}
