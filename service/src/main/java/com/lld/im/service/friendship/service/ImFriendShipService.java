package com.lld.im.service.friendship.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.common.model.RequestBase;
import com.lld.im.common.model.SyncReq;
import com.lld.im.service.friendship.model.req.*;

import java.util.List;

public interface ImFriendShipService {
    public ResponseVO ImportFriendShip(ImporFriendShipReq req);

    public ResponseVO addFriend(AddFriendReq req);

    public ResponseVO doAddFriend (RequestBase requestBase, String fromId, FriendDto dto, int appId);

    public ResponseVO updateFriend(UpdateFriendReq req);

    public ResponseVO deleteFriend(DeleteFriendReq req);

    public ResponseVO deleteAllFriend(DeleteFriendReq req);

    public ResponseVO getAllFriendShip(GetAllFriendShipReq req);

    public List<String> getAllFriendId(String userId, Integer appId);

    public ResponseVO getRelation(GetRelationReq req);

    public ResponseVO checkFriendShip(CheckFriendShipReq req);

    public ResponseVO addBlack(AddFriendShipBlackReq req);

    public ResponseVO deleteBlack(DeleteBlackReq req);

    public ResponseVO syncFriendshipList(SyncReq req);
}
