package com.lld.im.service.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.lld.im.codec.pack.conversation.DeleteConversationPack;
import com.lld.im.codec.pack.conversation.UpdateConversationPack;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.ConversationErrorCode;
import com.lld.im.common.enums.command.ConversationEventCommand;
import com.lld.im.common.enums.ConversationTypeEnum;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.common.model.SyncReq;
import com.lld.im.common.model.SyncResp;
import com.lld.im.common.model.message.MessageReadContent;
import com.lld.im.service.conversation.dao.ImConversationSetEntity;
import com.lld.im.service.conversation.dao.mapper.ImConversationSetMapper;
import com.lld.im.service.conversation.model.DeleteConversationReq;
import com.lld.im.service.conversation.model.UpdateConversationReq;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.MessageProducer;
import com.lld.im.service.utils.WriteUserSeq;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    @Autowired
    ImConversationSetMapper imConversationSetMapper;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    AppConfig appConfig;

    @Autowired
    RedisSeq redisSeq;

    @Autowired
    WriteUserSeq writeUserSeq;

    public String convertConversationId(Integer type, String fromId, String  toId) {
        return type + "_" + fromId + "_" + toId;
    }

    public void messageMarkRead(MessageReadContent messageReadContent){

        String toId = messageReadContent.getToId();
        if(messageReadContent.getConversationType() == ConversationTypeEnum.GROUP.getCode()){
            toId = messageReadContent.getGroupId();
        }

        String conversationId = convertConversationId(messageReadContent.getConversationType(),
                messageReadContent.getFromId(), toId);

        QueryWrapper<ImConversationSetEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("conversationId",conversationId);
        wrapper.eq("app_id",messageReadContent.getAppId());
        ImConversationSetEntity imConversationSetEntity = imConversationSetMapper.selectOne(wrapper);
        if(imConversationSetEntity == null){
            imConversationSetEntity = new ImConversationSetEntity();
            long seq = redisSeq.doGetSeq(messageReadContent.getAppId() + ":" + Constants.SeqConstants.Conversation);
            imConversationSetEntity.setConversationId(conversationId);
            BeanUtils.copyProperties(messageReadContent, imConversationSetEntity);
            imConversationSetEntity.setToId(toId);
            imConversationSetEntity.setReadSequence(messageReadContent.getMessageSequence());
            imConversationSetEntity.setSequence(seq);
            imConversationSetMapper.insert(imConversationSetEntity);
            writeUserSeq.writeUserSeq(messageReadContent.getAppId(),
                    messageReadContent.getFromId(), Constants.SeqConstants.Conversation, seq);
        }else{
            long seq = redisSeq.doGetSeq(messageReadContent.getAppId() + ":" + Constants.SeqConstants.Conversation);
            imConversationSetEntity.setSequence(seq);
            imConversationSetEntity.setReadSequence(messageReadContent.getMessageSequence());
            imConversationSetMapper.readMark(imConversationSetEntity);
            writeUserSeq.writeUserSeq(messageReadContent.getAppId(),
                    messageReadContent.getFromId(), Constants.SeqConstants.Conversation, seq);
        }
    }

    /**
     * @description: 删除会话
     * @param req
     * @return com.lld.im.common.ResponseVO
     */
    public ResponseVO deleteConversation(DeleteConversationReq req){

//        //置顶 有免打扰
//        QueryWrapper<ImConversationSetEntity> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq("conversation_id", req.getConversationId());
//        queryWrapper.eq("app_id", req.getAppId());
//        ImConversationSetEntity imConversationSetEntity = imConversationSetMapper.selectOne(queryWrapper);
//        if(imConversationSetEntity != null){
//            imConversationSetEntity.setIsMute(0);
//            imConversationSetEntity.setIsTop(0);
//            imConversationSetMapper.update(imConversationSetEntity,queryWrapper);
//        }

        if(appConfig.getDeleteConversationSyncMode() == 1){
            DeleteConversationPack pack = new DeleteConversationPack();
            pack.setConversationId(req.getConversationId());
            messageProducer.sendToUserExceptClient(req.getFromId(), ConversationEventCommand.CONVERSATION_DELETE,
                    pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));
        }
        return ResponseVO.successResponse();
    }

    /**
     * @description: 更新会话
     * @param req
     * @return com.lld.im.common.ResponseVO
     */
    public ResponseVO updateConversation(UpdateConversationReq req){

        if(req.getIsMute() == null && req.getIsTop() == null){
            //返回失败
            return ResponseVO.errorResponse(ConversationErrorCode.CONVERSATION_UPDATE_PARAM_ERROR);
        }
        QueryWrapper<ImConversationSetEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", req.getConversationId());
        queryWrapper.eq("app_id", req.getAppId());
        ImConversationSetEntity imConversationSetEntity = imConversationSetMapper.selectOne(queryWrapper);
        if(imConversationSetEntity != null){
            long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Conversation);

            if(req.getIsMute() != null){
                imConversationSetEntity.setIsMute(req.getIsMute());
            }
            if(req.getIsTop() != null){
                imConversationSetEntity.setIsTop(req.getIsTop());
            }

            imConversationSetEntity.setSequence(seq);
            imConversationSetMapper.update(imConversationSetEntity,queryWrapper);
            writeUserSeq.writeUserSeq(req.getAppId(),
                    req.getFromId(), Constants.SeqConstants.Conversation, seq);

            UpdateConversationPack pack = new UpdateConversationPack();
            pack.setConversationId(imConversationSetEntity.getConversationId());
            pack.setIsMute(imConversationSetEntity.getIsMute());
            pack.setIsTop(imConversationSetEntity.getIsTop());
            pack.setConversationType(imConversationSetEntity.getConversationType());
            pack.setSequence(seq);
            messageProducer.sendToUserExceptClient(req.getFromId(), ConversationEventCommand.CONVERSATION_UPDATE,
                    pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));
        }
        return ResponseVO.successResponse();
    }

    public ResponseVO syncConversationSet(SyncReq req) {
        if(req.getMaxLimit() > 100){
            req.setMaxLimit(100);
        }

        SyncResp<ImConversationSetEntity> resp = new SyncResp<>();
        //seq > req.getseq limit maxLimit
        QueryWrapper<ImConversationSetEntity> queryWrapper =
                new QueryWrapper<>();
        queryWrapper.eq("from_id",req.getOperater());
        queryWrapper.eq("app_id",req.getAppId());
        queryWrapper.gt("sequence",req.getLastSequence());
        queryWrapper.last(" limit " + req.getMaxLimit());
        queryWrapper.orderByAsc("sequence");
        List<ImConversationSetEntity> list = imConversationSetMapper
                .selectList(queryWrapper);

        if(!CollectionUtils.isEmpty(list)){
            ImConversationSetEntity maxSeqEntity = list.get(list.size() - 1);
            resp.setDataList(list);
            //设置最大seq
            Long conversationMaxSeq = imConversationSetMapper.geConversationSetMaxSeq(req.getAppId(), req.getOperater());
            resp.setMaxSequence(conversationMaxSeq);
            //设置是否拉取完毕
            resp.setCompleted(maxSeqEntity.getSequence() >= conversationMaxSeq);
            return ResponseVO.successResponse(resp);
        }

        resp.setCompleted(true);
        return ResponseVO.successResponse(resp);

    }
}
