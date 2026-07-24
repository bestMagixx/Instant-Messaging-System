package com.lld.im.codec.pack.message;

import lombok.Data;

@Data
public class MessageReceiveServerAckPack {

    private Long messageKey;

    private String fromId;

    private String toId;

    private Long messageSequence;

    private Boolean serverSend;
}
