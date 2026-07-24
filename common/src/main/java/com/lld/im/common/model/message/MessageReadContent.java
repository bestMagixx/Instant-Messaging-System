package com.lld.im.common.model.message;

import com.lld.im.common.model.ClientInfo;
import lombok.Data;

@Data
public class MessageReadContent extends ClientInfo {

    private long messageSequence;

    private String fromId;

    private String groupId;

    private String toId;

    private Integer conversationType;

}
