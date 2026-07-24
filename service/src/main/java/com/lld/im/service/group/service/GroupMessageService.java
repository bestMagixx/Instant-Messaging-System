package com.lld.im.service.group.service;

import com.lld.im.codec.pack.message.ChatMessageAck;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.command.GroupEventCommand;
import com.lld.im.common.enums.command.MessageCommand;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.common.model.message.GroupChatMessageContent;
import com.lld.im.common.model.message.MessageContent;
import com.lld.im.common.model.message.OfflineMessageContent;
import com.lld.im.service.message.model.req.SendGroupMessageReq;
import com.lld.im.service.message.model.resp.SendMessageResp;
import com.lld.im.service.message.service.CheckSendMessageService;
import com.lld.im.service.message.service.MessageStoreService;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.MessageProducer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GroupMessageService {

    @Autowired
    CheckSendMessageService checkSendMessageService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    ImGroupMemberService imGroupMemberService;

    @Autowired
    MessageStoreService messageStoreService;

    @Autowired
    RedisSeq redisSeq;

    private final ThreadPoolExecutor threadPoolExecutor;

    {
        AtomicInteger num = new AtomicInteger(0);
        threadPoolExecutor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("message-group-thread-" + num.getAndIncrement());
                return thread;
            }
        });
    }

    public void process(GroupChatMessageContent messageContent){

        String fromId = messageContent.getFromId();
        String groupId = messageContent.getGroupId();
        Integer appId = messageContent.getAppId();

        GroupChatMessageContent messageFromMessageIdCache = messageStoreService.getMessageFromMessageIdCache(
                messageContent.getAppId(), messageContent.getMessageId(), GroupChatMessageContent.class);

        if(messageFromMessageIdCache != null){
            threadPoolExecutor.execute(() -> {
                //1.回ack给自己
                ack(messageFromMessageIdCache,ResponseVO.successResponse());
                //2.发消息给同步在线端
                syncToSender(messageContent,messageFromMessageIdCache);
                //3.发消息给对方在线端
                dispatchMessage(messageFromMessageIdCache);
            });
            return;
        }

        long seq = redisSeq.doGetSeq(messageContent.getAppId() + ":" +
                Constants.SeqConstants.GroupMessage + messageContent.getGroupId());
        messageContent.setMessageSequence(seq);

        threadPoolExecutor.execute(() -> {
            messageStoreService.storeGroupMessage(messageContent);

            //插入离线消息
            List<String> groupMemberId = imGroupMemberService.getGroupMemberId(messageContent.getGroupId(),
                    messageContent.getAppId());
            messageContent.setMemberId(groupMemberId);
            OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
            BeanUtils.copyProperties(messageContent,offlineMessageContent);
            offlineMessageContent.setToId(messageContent.getGroupId());


            messageStoreService.storeGroupOfflineMessage(offlineMessageContent,groupMemberId);

            //1.回ack给自己
            ack(messageContent,ResponseVO.successResponse());
            //2.发消息给同步在线端
            syncToSender(messageContent,messageContent);
            //3.发消息给对方在线端
            dispatchMessage(messageContent);

            messageStoreService.setMessageFromMessageIdCache(
                    messageContent.getAppId(), messageContent.getMessageId(), messageContent);
        });

    }

    private void dispatchMessage(GroupChatMessageContent messageContent){
        for (String memberId : messageContent.getMemberId()) {
            if(memberId.equals(messageContent.getFromId())){
                messageProducer.sendToUser(memberId,
                        GroupEventCommand.MSG_GROUP,messageContent,messageContent.getAppId());
            }
        }
        messageProducer.sendToUser(messageContent.getToId(), MessageCommand.MSG_P2P,
                messageContent,messageContent.getAppId());

    }

    private void ack(MessageContent messageContent,ResponseVO responseVO){

        ChatMessageAck chatMessageAck = new ChatMessageAck(messageContent.getMessageId());
        responseVO.setData(chatMessageAck);
        //发消息
        messageProducer.sendToUser(messageContent.getFromId(), GroupEventCommand.GROUP_MSG_ACK,
                responseVO,messageContent);
    }

    private void syncToSender(GroupChatMessageContent messageContent, ClientInfo clientInfo){
        messageProducer.sendToUserExceptClient(messageContent.getFromId(), GroupEventCommand.MSG_GROUP,
                messageContent,messageContent);
    }

    private ResponseVO imServerPermissionCheck(String fromId,String toId,Integer appId){
        return checkSendMessageService.checkGroupMessage(fromId,toId,appId);
    }

    public SendMessageResp send(SendGroupMessageReq req) {

        SendMessageResp sendMessageResp = new SendMessageResp();
        GroupChatMessageContent message = new GroupChatMessageContent();
        BeanUtils.copyProperties(req, message);
        //插入数据
        messageStoreService.storeGroupMessage(message);
        sendMessageResp.setMessageKey(message.getMessageKey());
        sendMessageResp.setMessageTime(System.currentTimeMillis());

        //2.发消息给同步在线端
        syncToSender(message, message);
        //3.发消息给群内在线成员
        dispatchMessage(message);
        return sendMessageResp;
    }

}
