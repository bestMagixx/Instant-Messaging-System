package com.lld.im.common.model.message;

import com.lld.im.common.model.ClientInfo;
import lombok.Data;

@Data
public class MessageReceiveAckContent extends ClientInfo {

    private String messageKey;

    private String fromId;

    private String toId;

    private String messageSequence;
}
