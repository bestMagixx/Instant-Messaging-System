package com.lld.im.service.conversation.model;

import com.lld.im.common.model.RequestBase;
import lombok.Data;

@Data
public class UpdateConversationReq extends RequestBase {

    private String conversationId;

    private Integer isMute;

    private Integer isTop;

    private String fromId;

}
