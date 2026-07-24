package com.lld.im.service.friendship.model.callback;

import com.lld.im.service.friendship.model.req.FriendDto;
import lombok.Data;

@Data
public class AddFriendAfterCallbackDto {

    private String fromId;

    private FriendDto toItem;
}
