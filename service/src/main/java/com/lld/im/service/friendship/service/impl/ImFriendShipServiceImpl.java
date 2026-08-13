package com.lld.im.service.friendship.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lld.im.codec.pack.friendship.*;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.AllowFriendTypeEnum;
import com.lld.im.common.enums.CheckFriendShipTypeEnum;
import com.lld.im.common.enums.FriendShipErrorCode;
import com.lld.im.common.enums.FriendShipStatusEnum;
import com.lld.im.common.enums.command.FriendshipEventCommand;
import com.lld.im.common.model.RequestBase;
import com.lld.im.common.model.SyncReq;
import com.lld.im.common.model.SyncResp;
import com.lld.im.service.friendship.dao.ImFriendShipEntity;
import com.lld.im.service.friendship.dao.mapper.ImFriendShipMapper;
import com.lld.im.service.friendship.model.callback.AddFriendAfterCallbackDto;
import com.lld.im.service.friendship.model.callback.AddFriendBlackAfterCallbackDto;
import com.lld.im.service.friendship.model.callback.DeleteFriendAfterCallbackDto;
import com.lld.im.service.friendship.model.req.*;
import com.lld.im.service.friendship.model.resp.CheckFriendShipResp;
import com.lld.im.service.friendship.model.resp.ImportFriendShipResp;
import com.lld.im.service.friendship.service.ImFriendShipRequestService;
import com.lld.im.service.friendship.service.ImFriendShipService;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.user.dao.ImUserDataEntity;
import com.lld.im.service.user.service.ImUserService;
import com.lld.im.service.utils.CallbackService;
import com.lld.im.service.utils.MessageProducer;
import com.lld.im.service.utils.WriteUserSeq;
import io.reactivex.rxjava3.core.Flowable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImFriendShipServiceImpl implements ImFriendShipService {
    @Autowired
    ImFriendShipMapper imFriendShipMapper;

    @Autowired
    ImUserService imUserService;

    @Autowired
    ImFriendShipRequestService imFriendShipRequestService;

    @Autowired
    AppConfig appConfig;

    @Autowired
    CallbackService callbackService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    RedisSeq redisSeq;

    @Autowired
    WriteUserSeq writeUserSeq;

    @Override
    public ResponseVO ImportFriendShip(ImporFriendShipReq req) {
        if(req.getFriendItem().size() > 100){
            return ResponseVO.errorResponse(FriendShipErrorCode.IMPORT_SIZE_BEYOND);
        }
        ImportFriendShipResp resp = new ImportFriendShipResp();

        List<String> successId = new ArrayList<>();
        List<String> errorId = new ArrayList<>();

        for(ImporFriendShipReq.ImportFriendDto dto:
                req.getFriendItem()){
            ImFriendShipEntity entity = new ImFriendShipEntity();
            BeanUtils.copyProperties(dto,entity);
            entity.setAppId(req.getAppId());
            entity.setFromId(req.getFromId());
            try {
                int insert = imFriendShipMapper.insert(entity);
                if(insert == 1){
                    successId.add(dto.getToId());
                }else{
                    errorId.add(dto.getToId());
                }
            } catch (Exception e){
                e.printStackTrace();
                errorId.add(dto.getToId());
            }
        }

        resp.setSuccessId(successId);
        resp.setErrorId(errorId);

        return ResponseVO.successResponse(resp);
    }

    @Override
    public ResponseVO addFriend(AddFriendReq req) {

        ResponseVO<ImUserDataEntity> fromInfo = imUserService.getSingleUserInfo(req.getFromId(), req.getAppId());
        if(!fromInfo.isOk()){
            return fromInfo;
        }

        ResponseVO<ImUserDataEntity> toInfo = imUserService.getSingleUserInfo(req.getToItem().getToId(), req.getAppId());
        if(!toInfo.isOk()){
            return toInfo;
        }

        //之前回调
        if(appConfig.isAddFriendBeforeCallback()){
            ResponseVO responseVO = callbackService.beforeCallback(req.getAppId(), Constants.CallbackCommand.AddFriendBefore,
                    JSONObject.toJSONString(req));
            if(!responseVO.isOk()){
                return responseVO;
            }
        }


        ImUserDataEntity data = toInfo.getData();

        if(data.getFriendAllowType() != null && data.getFriendAllowType() == AllowFriendTypeEnum.NOT_NEED.getCode()){
            return this.doAddFriend(req,req.getFromId(), req.getToItem(), req.getAppId());
        }else{
            QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();

            query.eq("app_id", req.getAppId());
            query.eq("from_id",req.getFromId());
            query.eq("to_id",req.getToItem().getToId());

            ImFriendShipEntity fromItem = imFriendShipMapper.selectOne(query);

            if(fromItem == null || fromItem.getStatus() != FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()){
                //插入一条好友申请的数据
                ResponseVO responseVO = imFriendShipRequestService.addFriendRequest(req.getFromId(), req.getToItem(), req.getAppId());
                if(!responseVO.isOk()){
                    return responseVO;
                }
            }else{
                return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_YOUR_FRIEND);
            }


        }

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO updateFriend(UpdateFriendReq req) {
        ResponseVO<ImUserDataEntity> fromInfo = imUserService.getSingleUserInfo(req.getFromId(), req.getAppId());
        if(!fromInfo.isOk()){
            return fromInfo;
        }
         ResponseVO<ImUserDataEntity> toInfo = imUserService.getSingleUserInfo(req.getToItem().getToId(), req.getAppId());
        if(!toInfo.isOk()){
            return toInfo;
        }

        ResponseVO responseVO = doUpdateFriend(req.getFromId(), req.getToItem(), req.getAppId());
        if (responseVO.isOk()){
            UpdateFriendPack updateFriendPack = new UpdateFriendPack();
            updateFriendPack.setRemark(req.getToItem().getRemark());
            updateFriendPack.setToId(req.getToItem().getToId());
            messageProducer.sendToUser(req.getFromId(),req.getClientType(),
                    req.getImei(),FriendshipEventCommand.FRIEND_UPDATE,updateFriendPack,req.getAppId());

            //之后回调
            if(appConfig.isModifyFriendAfterCallback()){
                AddFriendAfterCallbackDto callbackDto = new AddFriendAfterCallbackDto();
                callbackDto.setFromId(req.getFromId());
                callbackDto.setToItem(req.getToItem());
                callbackService.callback(req.getAppId(),Constants.CallbackCommand.UpdateFriendAfter,
                        JSONObject.toJSONString(callbackDto));
            }

        }

        return responseVO;
    }

    @Override
    public ResponseVO deleteFriend(DeleteFriendReq req) {
        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();

        query.eq("app_id", req.getAppId());
        query.eq("from_id",req.getFromId());
        query.eq("to_id",req.getToId());

        ImFriendShipEntity fromItem = imFriendShipMapper.selectOne(query);

        if(fromItem == null){
            //返回不是好友
            return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_NOT_YOUR_FRIEND.getCode(),FriendShipErrorCode.TO_IS_NOT_YOUR_FRIEND.getError());
        }else{
            if(fromItem.getStatus() != null && fromItem.getStatus() == FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()){
                //执行删除操作
                ImFriendShipEntity update = new ImFriendShipEntity();
                long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Friendship);
                update.setFriendSequence(seq);
                update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_DELETE.getCode());

                imFriendShipMapper.update(update,query );
                writeUserSeq.writeUserSeq(req.getAppId(), req.getFromId(), Constants.SeqConstants.Friendship, seq);

                //发送包
                DeleteFriendPack deleteFriendPack = new DeleteFriendPack();
                deleteFriendPack.setFromId(req.getFromId());
                deleteFriendPack.setToId(req.getToId());
                deleteFriendPack.setSequence(seq);
                messageProducer.sendToUser(req.getFromId(),
                        req.getClientType(), req.getImei(),
                        FriendshipEventCommand.FRIEND_DELETE,
                        deleteFriendPack, req.getAppId());

                //之后回调
                if(appConfig.isDeleteFriendAfterCallback()){
                    DeleteFriendAfterCallbackDto callbackDto = new DeleteFriendAfterCallbackDto();
                    callbackDto.setFromId(req.getFromId());
                    FriendDto toItem = new FriendDto();
                    toItem.setToId(req.getToId());
                    callbackDto.setToId(toItem.getToId());
                    callbackService.callback(req.getAppId(),Constants.CallbackCommand.DeleteFriendAfter,
                            JSONObject.toJSONString(toItem));
                }

            }else{
                //返回已被删除
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_DELETED.getCode(),FriendShipErrorCode.FRIEND_IS_DELETED.getError());
            }
        }

        //发送包
        DeleteFriendPack deleteFriendPack = new DeleteFriendPack();
        deleteFriendPack.setFromId(req.getFromId());
        deleteFriendPack.setToId(req.getToId());
        messageProducer.sendToUser(req.getFromId(),
                req.getClientType(), req.getImei(),
                FriendshipEventCommand.FRIEND_DELETE,
                deleteFriendPack, req.getAppId());

        //之后回调
        if(appConfig.isDeleteFriendAfterCallback()){
            DeleteFriendAfterCallbackDto callbackDto = new DeleteFriendAfterCallbackDto();
            callbackDto.setFromId(req.getFromId());
            FriendDto toItem = new FriendDto();
            toItem.setToId(req.getToId());
            callbackDto.setToId(toItem.getToId());
            callbackService.callback(req.getAppId(),Constants.CallbackCommand.DeleteFriendAfter,
                    JSONObject.toJSONString(toItem));
        }

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO deleteAllFriend(DeleteFriendReq req) {

        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();

        query.eq("app_id", req.getAppId());
        query.eq("from_id",req.getFromId());
        query.eq("status",FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());

        ImFriendShipEntity update = new ImFriendShipEntity();
        update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_DELETE.getCode());

        imFriendShipMapper.update(update,query);

        //发送包
        DeleteAllFriendPack deleteFriendPack = new DeleteAllFriendPack();
        deleteFriendPack.setFromId(req.getFromId());
        messageProducer.sendToUser(req.getFromId(), req.getClientType(), req.getImei(), FriendshipEventCommand.FRIEND_ALL_DELETE,
                deleteFriendPack, req.getAppId());

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO getAllFriendShip(GetAllFriendShipReq req) {

        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("from_id",req.getFromId());
        query.eq("status",FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
         return ResponseVO.successResponse(imFriendShipMapper.selectList(query));
    }

    @Override
    public List<String> getAllFriendId(String userId, Integer appId) {
        return imFriendShipMapper.getAllFriendId(userId, appId);
    }

    @Override
    public ResponseVO getRelation(GetRelationReq req) {

        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("from_id",req.getFromId());
        query.eq("to_id",req.getToId());

        ImFriendShipEntity entity = imFriendShipMapper.selectOne(query);

        if(entity == null){
            //返回记录不存在
             return ResponseVO.errorResponse();
        }

        return ResponseVO.successResponse(entity);
    }

    @Override
    public ResponseVO checkFriendShip(CheckFriendShipReq req) {

        List<CheckFriendShipResp> resp = new ArrayList<>();

        if(req.getCheckType() == CheckFriendShipTypeEnum.SINGLE.getType()){
            return ResponseVO.successResponse(imFriendShipMapper.checkFriendShip(req));
        }else{

        }

        return null;
    }

    @Override
    public ResponseVO addBlack(AddFriendShipBlackReq req) {
        ResponseVO<ImUserDataEntity> fromInfo = imUserService.getSingleUserInfo(req.getFromId(), req.getAppId());
        if(!fromInfo.isOk()){
            return fromInfo;
        }
        ResponseVO<ImUserDataEntity> toInfo = imUserService.getSingleUserInfo(req.getToId(), req.getAppId());
        if(!toInfo.isOk()){
            return toInfo;
        }

        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper();
        query.eq("app_id",req.getAppId());
        query.eq("from_id",req.getFromId());
        query.eq("to_id",req.getToId());

        ImFriendShipEntity fromItem = imFriendShipMapper.selectOne(query);
        long seq = 0L;
        if(fromItem == null){
            //走添加逻辑
            seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Friendship);
            fromItem = new ImFriendShipEntity();
            fromItem.setFromId(req.getFromId());
            fromItem.setToId(req.getToId());
            fromItem.setFriendSequence(seq);
            fromItem.setAppId(req.getAppId());
            fromItem.setBlack(FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode());
            fromItem.setCreateTime(System.currentTimeMillis());
            int insert = imFriendShipMapper.insert(fromItem);
            if(insert != 1){
                return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
            }
            writeUserSeq.writeUserSeq(req.getAppId(), req.getFromId(), Constants.SeqConstants.Friendship, seq);
        }else{
            if (fromItem.getBlack()!= null && fromItem.getBlack() == FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode()) {
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_BLACK);
            } else {
                seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Friendship);

                ImFriendShipEntity update = new ImFriendShipEntity();
                update.setBlack(FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode());
                update.setFriendSequence(seq);
                int result = imFriendShipMapper.update(update,query);
                if(result != 1){
                    return ResponseVO.errorResponse(FriendShipErrorCode.ADD_BLACK_ERROR);
                }
                writeUserSeq.writeUserSeq(req.getAppId(), req.getFromId(), Constants.SeqConstants.Friendship, seq);

            }
        }

        //发送包
        AddFriendBlackPack addFriendBlackPack = new AddFriendBlackPack();
        addFriendBlackPack.setFromId(req.getFromId());
        addFriendBlackPack.setToId(req.getToId());
        addFriendBlackPack.setSequence(seq);
        messageProducer.sendToUser(req.getFromId(),req.getClientType(),req.getImei(),
                FriendshipEventCommand.FRIEND_BLACK_ADD,addFriendBlackPack,req.getAppId());

        //之后回调
        if(appConfig.isAddFriendShipBlackAfterCallback()){
            AddFriendBlackAfterCallbackDto callbackDto = new AddFriendBlackAfterCallbackDto();
            callbackDto.setFromId(req.getFromId());
            callbackDto.setToId(req.getToId());
            callbackService.callback(req.getAppId(),Constants.CallbackCommand.AddBlackAfter,
                    JSONObject.toJSONString(callbackDto));
        }

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO deleteBlack(DeleteBlackReq req) {

        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper();
        query.eq("app_id",req.getAppId());
        query.eq("from_id",req.getFromId());
        query.eq("to_id",req.getToId());

        ImFriendShipEntity entity = imFriendShipMapper.selectOne(query);
        if(entity != null && entity.getBlack() == FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode()){
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_NOT_YOUR_BLACK);
        }

        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Friendship);

        ImFriendShipEntity update = new ImFriendShipEntity();
        update.setFriendSequence(seq);
        update.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
        int update1 = imFriendShipMapper.update(update, query);


        if(update1 == 1){
            writeUserSeq.writeUserSeq(req.getAppId(), req.getFromId(), Constants.SeqConstants.Friendship, seq);

            DeleteBlackPack deleteBlackPack = new DeleteBlackPack();
            deleteBlackPack.setFromId(req.getFromId());
            deleteBlackPack.setToId(req.getToId());
            deleteBlackPack.setSequence(seq);
            messageProducer.sendToUser(req.getFromId(), null, "",
                    FriendshipEventCommand.FRIEND_BLACK_DELETE, deleteBlackPack,req.getAppId());

            //之后回调
            if (appConfig.isAddFriendShipBlackAfterCallback()){
                AddFriendBlackAfterCallbackDto callbackDto = new AddFriendBlackAfterCallbackDto();
                callbackDto.setFromId(req.getFromId());
                callbackDto.setToId(req.getToId());
                callbackService.beforeCallback(req.getAppId(),
                        Constants.CallbackCommand.DeleteBlack, JSONObject
                                .toJSONString(callbackDto));
            }
        }

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO syncFriendshipList(SyncReq req) {

        if(req.getMaxLimit() > 100){
            req.setMaxLimit(100);
        }

        SyncResp<ImFriendShipEntity> resp = new SyncResp<>();
        //seq > req.getSeq limit maxLimit
        QueryWrapper<ImFriendShipEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("from_id",req.getOperater());
        queryWrapper.eq("app_id",req.getAppId());
        queryWrapper.gt("friend_sequence", req.getLastSequence());
        queryWrapper.last("limit" + req.getMaxLimit());
        queryWrapper.orderByAsc("friend_sequence");
        List<ImFriendShipEntity> list = imFriendShipMapper.selectList(queryWrapper);

        if(!CollectionUtils.isEmpty(list)){
            ImFriendShipEntity maxSeqEntity = list.get(list.size() - 1);
            resp.setDataList(list);
            //设置最大seq
            Long friendShipMaxSeq = imFriendShipMapper.getFriendShipMaxSeq(req.getAppId(), req.getOperater());
            resp.setMaxSequence(friendShipMaxSeq);
            //设置是否拉取完毕
            resp.setCompleted(maxSeqEntity.getFriendSequence() >= friendShipMaxSeq);
            return ResponseVO.successResponse(resp);
        }

        resp.setCompleted(true);
        return ResponseVO.successResponse(resp);
    }

    @Transactional
    public ResponseVO doUpdateFriend(String fromId, FriendDto dto, int appId){

        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);

        UpdateWrapper<ImFriendShipEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().set(ImFriendShipEntity::getAddSource,dto.getAddSource())
                .set(ImFriendShipEntity::getRemark,dto.getRemark())
                .set(ImFriendShipEntity::getFriendSequence,seq)
                .set(ImFriendShipEntity::getExtra,dto.getExtra())
                .eq(ImFriendShipEntity::getAppId,appId)
                .eq(ImFriendShipEntity::getFromId,fromId)
                .eq(ImFriendShipEntity::getToId,dto.getToId());


        int update = imFriendShipMapper.update(null, updateWrapper);
        if(update == 1){
            return ResponseVO.successResponse();
        }

        writeUserSeq.writeUserSeq(appId,fromId,Constants.SeqConstants.Friendship,seq);

        return ResponseVO.errorResponse();
    }

    /**
     *
     * @param fromId
     * @param dto
     * @param appId
     *
     * 真正执行添加好友的方法
     * @return
     */
    @Override
    @Transactional
    public ResponseVO doAddFriend (RequestBase requestBase,String fromId, FriendDto dto, int appId){

        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();

        query.eq("app_id", appId);
        query.eq("from_id",fromId);
        query.eq("to_id",dto.getToId());

        ImFriendShipEntity fromItem = imFriendShipMapper.selectOne(query);

        long seq = 0L;

        if(fromItem == null){
            //添加
            fromItem = new ImFriendShipEntity();
            seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);
            fromItem.setFriendSequence(seq);
            fromItem.setFromId(fromId);
            fromItem.setAppId(appId);
            BeanUtils.copyProperties(dto,fromItem);
            fromItem.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
            fromItem.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
            fromItem.setCreateTime(System.currentTimeMillis());
            int insert = imFriendShipMapper.insert(fromItem);
            if(insert != 1){
                return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
            }
            writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship,seq);
        }else{
            if(fromItem.getStatus() == FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()){
                return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_YOUR_FRIEND);
            }
            else{
                ImFriendShipEntity update = new ImFriendShipEntity();
                if(StringUtils.isNotBlank(dto.getAddSource())){
                    update.setAddSource(dto.getAddSource());
                }
                if(StringUtils.isNotBlank(dto.getRemark())){
                    update.setRemark(dto.getRemark());
                }
                if(StringUtils.isNotBlank(dto.getExtra())){
                    update.setExtra(dto.getExtra());
                }
                seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);
                update.setFriendSequence(seq);
                update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());

                int result = imFriendShipMapper.update(update, query);
                if(result != 1){
                    return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
                }
                writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Friendship,seq);
            }
        }

        QueryWrapper<ImFriendShipEntity> toQuery = new QueryWrapper<>();
        toQuery.eq("app_id",appId);
        toQuery.eq("from_id",dto.getToId());
        toQuery.eq("to_id",fromId);
        ImFriendShipEntity toItem = imFriendShipMapper.selectOne(toQuery);
        if(toItem == null){
            toItem = new ImFriendShipEntity();
            toItem.setFromId(dto.getToId());
            toItem.setAppId(appId);
            BeanUtils.copyProperties(dto,toItem);
            toItem.setToId(fromId);
            toItem.setFriendSequence(seq);
            toItem.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
            toItem.setCreateTime(System.currentTimeMillis());
            toItem.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
            int insert = imFriendShipMapper.insert(toItem);
            if(insert != 1){
                return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
            }
            writeUserSeq.writeUserSeq(appId, toItem.getToId(), Constants.SeqConstants.Friendship,seq);
        }else{
            if(toItem.getStatus() != FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()){
                ImFriendShipEntity update = new ImFriendShipEntity();
                update.setFriendSequence(seq);
                update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());

                int result = imFriendShipMapper.update(update, toQuery);
                if(result != 1){
                    return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
                }
                writeUserSeq.writeUserSeq(appId, toItem.getToId(), Constants.SeqConstants.Friendship,seq);
            }
        }

        //发送给from
        AddFriendPack addFriendPack = new AddFriendPack();
        BeanUtils.copyProperties(fromItem,addFriendPack);
        addFriendPack.setSequence(seq);
        if(requestBase != null){
            messageProducer.sendToUser(fromId,requestBase.getClientType(),requestBase.getImei(),
                    FriendshipEventCommand.FRIEND_ADD,addFriendPack, requestBase.getAppId());
        }else{
            messageProducer.sendToUser(fromId,FriendshipEventCommand.FRIEND_ADD,addFriendPack,
                    requestBase.getAppId());
        }

        //发送给to

        AddFriendPack addFriendToPack = new AddFriendPack();
        BeanUtils.copyProperties(toItem,addFriendToPack);
        messageProducer.sendToUser(toItem.getFromId(),FriendshipEventCommand.FRIEND_ADD,addFriendToPack,
                requestBase.getAppId());
        //之后回调

        AddFriendAfterCallbackDto callbackDto = new AddFriendAfterCallbackDto();
        callbackDto.setFromId(fromId);
        callbackDto.setToItem(dto);


        if(appConfig.isAddFriendAfterCallback()){
            callbackService.callback(appId,Constants.CallbackCommand.AddFriendAfter,
                    JSONObject.toJSONString(callbackDto));
        }

        return ResponseVO.successResponse();
    }
}
