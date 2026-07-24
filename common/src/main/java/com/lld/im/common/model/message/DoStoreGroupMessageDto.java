package com.lld.im.common.model.message;

import lombok.Data;

@Data
public class DoStoreGroupMessageDto {

    private GroupChatMessageContent groupChatMessageContent;

    private ImMessageBody messageBody;
}
