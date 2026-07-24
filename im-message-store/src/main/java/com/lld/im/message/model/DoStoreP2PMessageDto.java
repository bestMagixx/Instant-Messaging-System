package com.lld.im.message.model;


import com.lld.im.common.model.message.MessageContent;
import com.lld.im.message.dao.ImMessageBodyEntity;
import lombok.Data;

@Data
public class DoStoreP2PMessageDto {

    private MessageContent messageContent;

    private ImMessageBodyEntity messageBodyEntity;

}
