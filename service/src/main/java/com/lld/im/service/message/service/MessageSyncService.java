package com.lld.im.service.message.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.lld.im.codec.pack.message.MessageReadPack;
import com.lld.im.codec.pack.message.RecallMessageNotifyPack;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.ConversationTypeEnum;
import com.lld.im.common.enums.DelFlagEnum;
import com.lld.im.common.enums.MessageErrorCode;
import com.lld.im.common.enums.command.Command;
import com.lld.im.common.enums.command.GroupEventCommand;
import com.lld.im.common.enums.command.MessageCommand;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.common.model.SyncReq;
import com.lld.im.common.model.SyncResp;
import com.lld.im.common.model.message.MessageReadContent;
import com.lld.im.common.model.message.MessageReceiveAckContent;
import com.lld.im.common.model.message.OfflineMessageContent;
import com.lld.im.common.model.message.RecallMessageContent;
import com.lld.im.service.conversation.service.ConversationService;
import com.lld.im.service.group.service.ImGroupMemberService;
import com.lld.im.service.message.dao.ImMessageBodyEntity;
import com.lld.im.service.message.dao.mapper.ImMessageBodyMapper;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.ConversationIdGenerate;
import com.lld.im.service.utils.MessageProducer;
import com.lld.im.service.utils.SnowflakeIdWorker;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class MessageSyncService {


    @Autowired
    MessageProducer messageProducer;

    @Autowired
    ConversationService conversationService;

    @Autowired
    ImMessageBodyMapper imMessageBodyMapper;

    @Autowired
    RedisSeq redisSeq;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    SnowflakeIdWorker snowflakeIdWorker;

    @Autowired
    ImGroupMemberService imGroupMemberService;

    public void receiveMark(MessageReceiveAckContent messageReceiveAckContent){

        messageProducer.sendToUser(messageReceiveAckContent.getToId(),
                MessageCommand.MSG_RECIVE_ACK,messageReceiveAckContent,messageReceiveAckContent.getAppId());

    }

    /**
     * @description 消息已读,更新会话的seq，通知在线的同步端发送指定command，发送通知对方（消息发起方）我已读
     * @param messageReadContent
     */
    public void readMark(MessageReadContent messageReadContent) {
        conversationService.messageMarkRead(messageReadContent);
        MessageReadPack messageReadPack = new MessageReadPack();
        BeanUtils.copyProperties(messageReadContent,messageReadPack);
        syncToSender(messageReadPack, messageReadContent, MessageCommand.MSG_READED_NOTIFY);
        //发送给对方
        messageProducer.sendToUser(messageReadContent.getToId(),
                MessageCommand.MSG_READED_RECEIPT,messageReadPack, messageReadContent.getAppId());
    }

    public void syncToSender(MessageReadPack messageReadPack, MessageReadContent messageContent, Command command) {
        //发送给自己的其他的端
        messageProducer.sendToUserExceptClient(messageReadPack.getFromId(),
                command, messageReadPack,messageContent);
    }

    public void groupReadMark(MessageReadContent messageReadContent) {
        conversationService.messageMarkRead(messageReadContent);
        MessageReadPack messageReadPack = new MessageReadPack();
        BeanUtils.copyProperties(messageReadContent,messageReadPack);
        syncToSender(messageReadPack, messageReadContent, GroupEventCommand.MSG_GROUP_READED_NOTIFY);
        messageProducer.sendToUser(messageReadPack.getToId(),
                GroupEventCommand.MSG_GROUP_READED_RECEIPT,
                messageReadContent, messageReadContent.getAppId());
    }

    public ResponseVO syncOfflineMessage(SyncReq req) {

        SyncResp<OfflineMessageContent> resp = new SyncResp<>();

        String key = req.getAppId() + ":" + Constants.RedisConstants.OfflineMessage + ":" + req.getOperater();
        //获取最大的seq
        Long maxSeq = 0L;
        ZSetOperations zSetOperations = redisTemplate.opsForZSet();
        Set set = zSetOperations.reverseRangeWithScores(key, 0, 0);
        if(!CollectionUtils.isEmpty(set)){
            List list = new ArrayList(set);
            DefaultTypedTuple o = (DefaultTypedTuple) list.get(0);
            maxSeq = o.getScore().longValue();
        }

        List<OfflineMessageContent> respList = new ArrayList<>();
        resp.setMaxSequence(maxSeq);

        Set<ZSetOperations.TypedTuple> querySet = zSetOperations.rangeByScoreWithScores(key,
                req.getLastSequence(), maxSeq, 0, req.getMaxLimit());
        for (ZSetOperations.TypedTuple<String> typedTuple : querySet) {
            String value = typedTuple.getValue();
            OfflineMessageContent offlineMessageContent = JSONObject.parseObject(value, OfflineMessageContent.class);
            respList.add(offlineMessageContent);
        }
        resp.setDataList(respList);

        if(!CollectionUtils.isEmpty(respList)){
            OfflineMessageContent offlineMessageContent = respList.get(respList.size() - 1);
            resp.setCompleted(maxSeq <= offlineMessageContent.getMessageKey());
        }

        return ResponseVO.successResponse(resp);
    }

    //修改历史消息的状态
    //修改离线消息的状态
    //ack发下哦那个给发送方
    //发送给同步端
    //分发消息给消息的接收方
    public void recallMessage(RecallMessageContent messageContent) {

        Long messageTime = messageContent.getMessageTime();
        Long now = System.currentTimeMillis();

        RecallMessageNotifyPack pack = new RecallMessageNotifyPack();
        BeanUtils.copyProperties(messageContent, pack);

        if(120000L < now - messageTime){
            //ack失败，超过一定时间的消息不允许撤回
            recallAck(pack, ResponseVO.errorResponse(MessageErrorCode.MESSAGE_RECALL_TIME_OUT),messageContent);
            return;
        }

        QueryWrapper<ImMessageBodyEntity> query = new QueryWrapper<>();
        query.eq("app_id",messageContent.getAppId());
        query.eq("message_key",messageContent.getMessageKey());
        ImMessageBodyEntity messageBodyEntity = imMessageBodyMapper.selectOne(query);
        if(messageBodyEntity == null){
            //消息不存在
            recallAck(pack, ResponseVO.errorResponse(MessageErrorCode.MESSAGEBODY_IS_NOT_EXIST),messageContent);
            return;
        }

        if(messageBodyEntity.getDelFlag() == DelFlagEnum.DELETE.getCode()){
            //ack失败 已经撤回的消息不能再撤回
            recallAck(pack, ResponseVO.errorResponse(MessageErrorCode.MESSAGE_IS_RECALLED),messageContent);
            return;
        }

        messageBodyEntity.setDelFlag(DelFlagEnum.DELETE.getCode());
        imMessageBodyMapper.update(messageBodyEntity,query);

        if(messageContent.getConversationType() == ConversationTypeEnum.P2P.getCode()){
            // 找到fromId的队列
            String fromKey = messageContent.getAppId() + ":" + Constants.RedisConstants.OfflineMessage + ":" + messageContent.getFromId();
            // 找到toId的队列
            String toKey = messageContent.getAppId() + ":" + Constants.RedisConstants.OfflineMessage + ":" + messageContent.getToId();

            //获取offlineMessageContent
            OfflineMessageContent offlineMessageContent = getOfflineMessageContent(messageContent, messageBodyEntity);

            long messageKey = SnowflakeIdWorker.nextId();

            redisTemplate.opsForZSet().add(fromKey, JSONObject.toJSONString(offlineMessageContent), messageKey);
            redisTemplate.opsForZSet().add(toKey, JSONObject.toJSONString(offlineMessageContent), messageKey);

            //ack
            recallAck(pack, ResponseVO.successResponse(), messageContent);
            //分发给同步端
            messageProducer.sendToUserExceptClient(messageContent.getFromId(),
                    MessageCommand.MSG_RECALL_NOTIFY, pack, messageContent);
            //分发给对方
            messageProducer.sendToUser(messageContent.getToId(), MessageCommand.MSG_RECALL_NOTIFY,
                    pack, messageContent);
        }else {
            // 找到fromId的队列
            String fromKey = messageContent.getAppId() + ":" + Constants.RedisConstants.OfflineMessage + ":" + messageContent.getFromId();
            // 找到群成员Id
            String toKey;

            List<String> membersId = imGroupMemberService.getGroupMemberId(messageContent.getToId(), messageContent.getAppId());

            //获取offlineMessageContent
            OfflineMessageContent offlineMessageContent = getOfflineMessageContent(messageContent, messageBodyEntity);

            long messageKey = SnowflakeIdWorker.nextId();

            redisTemplate.opsForZSet().add(fromKey, JSONObject.toJSONString(offlineMessageContent), messageKey);

            for(String memberId : membersId){
                toKey = messageContent.getAppId() + ":" + Constants.RedisConstants.OfflineMessage + ":" + memberId;
                redisTemplate.opsForZSet().add(toKey, JSONObject.toJSONString(offlineMessageContent), messageKey);
            }

            //ack
            recallAck(pack, ResponseVO.successResponse(), messageContent);
            //分发给同步端
            messageProducer.sendToUserExceptClient(messageContent.getFromId(),
                    MessageCommand.MSG_RECALL_NOTIFY, pack, messageContent);
            //分发给群成员
            for(String memberId : membersId){
                messageProducer.sendToUser(memberId, MessageCommand.MSG_RECALL_NOTIFY,
                        pack, messageContent);
            }
        }

    }

    public OfflineMessageContent getOfflineMessageContent(RecallMessageContent messageContent, ImMessageBodyEntity messageBodyEntity){
        OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
        BeanUtils.copyProperties(messageContent, offlineMessageContent);
        offlineMessageContent.setDelFlag(DelFlagEnum.DELETE.getCode());
        offlineMessageContent.setConversationType(messageContent.getConversationType());
        offlineMessageContent.setConversationId(conversationService.convertConversationId(
                messageContent.getConversationType(), messageContent.getFromId(), messageContent.getToId()
        ));
        offlineMessageContent.setMessageBody(messageBodyEntity.getMessageBody());

        long seq;
        seq = redisSeq.doGetSeq(messageContent.getAppId() + ":" + Constants.SeqConstants.Message + ":" +
                ConversationIdGenerate.generateP2PId(messageContent.getFromId(), messageContent.getToId()));
        offlineMessageContent.setMessageSequence(seq);

        return offlineMessageContent;
    }
    private void recallAck(RecallMessageNotifyPack recallPack, ResponseVO<Object> success, ClientInfo clientInfo) {
        ResponseVO<Object> wrappedResp = success;
        messageProducer.sendToUser(recallPack.getFromId(),
                MessageCommand.MSG_RECALL_ACK, wrappedResp, clientInfo);
    }
}
