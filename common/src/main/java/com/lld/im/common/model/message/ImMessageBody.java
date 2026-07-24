package com.lld.im.common.model.message;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class ImMessageBody {

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
