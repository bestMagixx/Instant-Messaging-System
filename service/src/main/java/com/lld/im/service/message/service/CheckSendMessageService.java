package com.lld.im.service.message.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.enums.*;
import com.lld.im.service.friendship.dao.ImFriendShipEntity;
import com.lld.im.service.friendship.model.req.GetRelationReq;
import com.lld.im.service.friendship.service.ImFriendShipService;
import com.lld.im.service.group.dao.ImGroupEntity;
import com.lld.im.service.group.model.resp.GetRoleInGroupResp;
import com.lld.im.service.group.service.ImGroupMemberService;
import com.lld.im.service.group.service.ImGroupService;
import com.lld.im.service.user.dao.ImUserDataEntity;
import com.lld.im.service.user.service.ImUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CheckSendMessageService {

    @Autowired
    ImUserService imUserService;

    @Autowired
    ImFriendShipService imFriendShipService;

    @Autowired
    ImGroupService imGroupService;

    @Autowired
    ImGroupMemberService imGroupMemberService;

    @Autowired
    AppConfig appConfig;

    public ResponseVO checkSenderForVidAndMute(String fromId,Integer appId){
        ResponseVO<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(fromId, appId);
        if(!singleUserInfo.isOk()){
            System.out.println("查询发送者信息失败");
            return  singleUserInfo;
        }

        ImUserDataEntity user = singleUserInfo.getData();
        if(user.getForbiddenFlag() == UserForbiddenFlagEnum.FORBIBBEN.getCode()){
            System.out.println("发送者被封号");
            return ResponseVO.errorResponse(MessageErrorCode.FROMER_IS_FORBIBBEN);
        }else if(user.getSilentFlag() == UserSilentFlagEnum.MUTE.getCode()){
            System.out.println("发送者被禁言");
            return ResponseVO.errorResponse(MessageErrorCode.FROMER_IS_MUTE);
        }

        return ResponseVO.successResponse();
    }

    public ResponseVO checkFriendShip(String fromId,String toId,Integer appId){

        if(appConfig.isSendMessageCheckFriend()){
            GetRelationReq fromReq = new GetRelationReq();
            fromReq.setFromId(fromId);
            fromReq.setToId(toId);
            fromReq.setAppId(appId);
            ResponseVO<ImFriendShipEntity> fromRelation = imFriendShipService.getRelation(fromReq);
            if(!fromRelation.isOk()){
                System.out.println("查询发送者好友信息失败");
                return fromRelation;
            }

            GetRelationReq toReq = new GetRelationReq();
            toReq.setFromId(toId);
            toReq.setToId(fromId);
            toReq.setAppId(appId);
            ResponseVO<ImFriendShipEntity> toRelation = imFriendShipService.getRelation(toReq);
            if(!toRelation.isOk()){
                System.out.println("查询接收者好友信息失败");
                return toRelation;
            }

            if(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()
                    != fromRelation.getData().getStatus()){
                System.out.println("发送者已删除接收者");
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_DELETED);
            }

            if(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()
                    != toRelation.getData().getStatus()){
                System.out.println("接收者已删除发送者");
                return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_NOT_YOUR_FRIEND);
            }

            if(appConfig.isSendMessageCheckBlack()){
                if(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode()
                != fromRelation.getData().getBlack()){
                    System.out.println("你已拉黑对方");
                    return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_BLACK);
                }

                if(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode()
                != toRelation.getData().getBlack()){
                    System.out.println("你已被对方拉黑");
                    return ResponseVO.errorResponse(FriendShipErrorCode.TARGET_IS_BLACK_YOU);
                }
            }
        }

        return ResponseVO.successResponse();
    }

    public ResponseVO checkGroupMessage(String fromId,String groupId,Integer appId){

        ResponseVO responseVO = checkSenderForVidAndMute(fromId, appId);
        if(!responseVO.isOk()){
            return responseVO;
        }

        //判断群逻辑
        ResponseVO<ImGroupEntity> group = imGroupService.getGroup(groupId, appId);
        if(!group.isOk()){
            return group;
        }

        //判断群成员是否在群内
        ResponseVO<GetRoleInGroupResp> roleInGroupOne = imGroupMemberService.getRoleInGroupOne(groupId, fromId, appId);
        if(!roleInGroupOne.isOk()){
            return roleInGroupOne;
        }
        GetRoleInGroupResp data = roleInGroupOne.getData();

        //判断群是否被禁言
        //如果禁言，只有群管理何群主可以发言
        ImGroupEntity groupData = group.getData();
        if(groupData.getMute() == GroupMuteTypeEnum.MUTE.getCode()
            && (data.getRole() == GroupMemberRoleEnum.MAMAGER.getCode()
                || data.getRole() == GroupMemberRoleEnum.OWNER.getCode())){
            return ResponseVO.errorResponse(GroupErrorCode.THIS_GROUP_IS_MUTE);
        }

        if(data.getSpeakDate() != null && data.getSpeakDate() > System.currentTimeMillis()){
            return ResponseVO.errorResponse(GroupErrorCode.GROUP_MEMBER_IS_SPEAK);
        }

        return ResponseVO.successResponse();
    }
}
