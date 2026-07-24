package com.lld.im.codec.pack.message;

import lombok.Data;

@Data
public class MessageReadPack {

    private long messageSequence;

    private String fromId;

    private String groupId;

    private String toId;

    private Integer conversationType;
}
