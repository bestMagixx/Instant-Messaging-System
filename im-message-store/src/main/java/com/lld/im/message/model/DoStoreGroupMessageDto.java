package com.lld.im.message.model;

import com.lld.im.common.model.message.GroupChatMessageContent;
import com.lld.im.message.dao.ImMessageBodyEntity;
import lombok.Data;

@Data
public class DoStoreGroupMessageDto {

    private GroupChatMessageContent groupChatMessageContent;

    private ImMessageBodyEntity imMessageBodyEntity;
}
