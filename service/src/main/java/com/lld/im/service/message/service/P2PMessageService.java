package com.lld.im.service.message.service;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.pack.message.ChatMessageAck;
import com.lld.im.codec.pack.message.MessageReceiveServerAckPack;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.ConversationTypeEnum;
import com.lld.im.common.enums.command.MessageCommand;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.common.model.message.MessageContent;
import com.lld.im.common.model.message.OfflineMessageContent;
import com.lld.im.service.message.model.req.SendMessageReq;
import com.lld.im.service.message.model.resp.SendMessageResp;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.CallbackService;
import com.lld.im.service.utils.ConversationIdGenerate;
import com.lld.im.service.utils.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class P2PMessageService {

    private Logger logger = LoggerFactory.getLogger(P2PMessageService.class);

    @Autowired
    CheckSendMessageService checkSendMessageService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    MessageStoreService messageStoreService;

    @Autowired
    RedisSeq redisSeq;

    @Autowired
    AppConfig appConfig;

    @Autowired
    CallbackService callbackService;

    private final ThreadPoolExecutor threadPoolExecutor;

    {
        AtomicInteger num = new AtomicInteger(0);

        threadPoolExecutor = new ThreadPoolExecutor(8, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(1000), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("message-process-thread-" + num.getAndIncrement());
                return thread;
            }
        });
    }

    public void process(MessageContent messageContent){

        String fromId = messageContent.getFromId();
        String toId = messageContent.getToId();
        Integer appId = messageContent.getAppId();

        //前置校验
        //这个用户是否被禁言 是否被禁用
        //发送方和接收方是否是好友
//        ResponseVO responseVO = imServerPermissionCheck(fromId,toId,appId);
//        if(responseVO.isOk()){

        //用messageId从缓存中获取消息
        MessageContent messageFromMessageIdCache = messageStoreService.getMessageFromMessageIdCache(
                messageContent.getAppId(), messageContent.getMessageId(), MessageContent.class);
        if(messageFromMessageIdCache != null){
            threadPoolExecutor.execute(() -> {
                //1.回ack给自己
                ack(messageContent,ResponseVO.successResponse());
                //2.发消息给同步在线端
                syncToSender(messageContent, messageFromMessageIdCache);
                //3.发消息给对方在线端
                List<ClientInfo> clientInfos = dispatchMessage(messageFromMessageIdCache);
                if(clientInfos.isEmpty()){
                    //发送接受确认给接收方，要带上是服务端发送的标识
                    receiveAck(messageContent);
                }
            });
            return;
        }

        //回调
        ResponseVO responseVO = ResponseVO.successResponse();
        if(appConfig.isSendMessageAfterCallback()){
            responseVO = callbackService.beforeCallback(messageContent.getAppId(), Constants.CallbackCommand.SendMessageBefore
                    , JSONObject.toJSONString(messageContent));
        }

        if(!responseVO.isOk()){
            ack(messageContent,responseVO);
            return;
        }

        //appId + Seq + (from + to) groupId
        long seq = redisSeq.doGetSeq(messageContent.getAppId() + ":"
                + Constants.SeqConstants.Message + ":" + ConversationIdGenerate.generateP2PId(
                messageContent.getFromId(), messageContent.getToId()
        ));
        messageContent.setMessageSequence(seq);

            threadPoolExecutor.execute(() -> {
                //插入数据
                messageStoreService.storeP2PMessage(messageContent);

                //插入离线消息
                OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
                BeanUtils.copyProperties(messageContent,offlineMessageContent);
                offlineMessageContent.setConversationType(ConversationTypeEnum.P2P.getCode());
                messageStoreService.storeOfflineMessage(offlineMessageContent);

                //1.回ack给自己
                ack(messageContent,ResponseVO.successResponse());
                //2.发消息给同步在线端
                syncToSender(messageContent,messageContent);
                //3.发消息给对方在线端
                List<ClientInfo> clientInfos = dispatchMessage(messageContent);
                //将messageId存到缓存中
                messageStoreService.setMessageFromMessageIdCache(messageContent.getAppId(), messageContent.getMessageId(), messageContent);
                if(clientInfos.isEmpty()){
                    //发送接受确认给接收方，要带上是服务端发送的标识
                    receiveAck(messageContent);
                }

                if(appConfig.isSendMessageAfterCallback()){
                    callbackService.callback(messageContent.getAppId(),Constants.CallbackCommand.SendMessageAfter,
                            JSONObject.toJSONString(messageContent));
                }

            });

//        }else {
//            //告诉客户端失败了
//            //ack
//            ack(messageContent,responseVO);
//        }

    }

    private List<ClientInfo> dispatchMessage(MessageContent messageContent){

        List<ClientInfo> clientInfos = messageProducer.sendToUser(messageContent.getToId(), MessageCommand.MSG_P2P,
                messageContent, messageContent.getAppId());

        return clientInfos;
    }

    private void ack(MessageContent messageContent,ResponseVO responseVO){
        logger.info("msg ack,msgId={},checkResult={}",messageContent.getMessageId(),responseVO.getCode());

        ChatMessageAck chatMessageAck = new ChatMessageAck(
                messageContent.getMessageId(), messageContent.getMessageSequence());
        responseVO.setData(chatMessageAck);
        //发消息
        messageProducer.sendToUser(messageContent.getFromId(), MessageCommand.MSG_ACK,
                responseVO,messageContent);
    }

    public void receiveAck(MessageContent messageContent){
        MessageReceiveServerAckPack pack = new MessageReceiveServerAckPack();
        pack.setFromId(messageContent.getFromId());
        pack.setToId(messageContent.getToId());
        pack.setMessageKey(messageContent.getMessageKey());
        pack.setMessageSequence(messageContent.getMessageSequence());
        pack.setServerSend(true);
        messageProducer.sendToUser(messageContent.getFromId(), MessageCommand.MSG_RECIVE_ACK,pack,
                new ClientInfo(messageContent.getAppId(), messageContent.getClientType(), messageContent.getImei()));
    }

    private void syncToSender(MessageContent messageContent, ClientInfo clientInfo){
        messageProducer.sendToUserExceptClient(messageContent.getFromId(), MessageCommand.MSG_P2P,
                messageContent,messageContent);
    }

    public ResponseVO imServerPermissionCheck(String fromId,String toId,
                                               Integer appId){
        System.out.println("开始校验消息");
        ResponseVO responseVO = checkSendMessageService.checkSenderForVidAndMute(fromId,
                appId);
        if(!responseVO.isOk()){
            System.out.println("校验ForVidAndMute失败");
            return responseVO;
        }

        responseVO = checkSendMessageService.checkFriendShip(fromId, toId,
                appId);
        if(!responseVO.isOk()){
            System.out.println("校验FriendShip失败");
            return responseVO;
        }

        System.out.println("消息校验成功");
        return ResponseVO.successResponse();
    }

    public SendMessageResp send(SendMessageReq req) {

        SendMessageResp sendMessageResp = new SendMessageResp();
        MessageContent message = new MessageContent();
        BeanUtils.copyProperties(req,message);
        //插入数据
        messageStoreService.storeP2PMessage(message);
        sendMessageResp.setMessageKey(message.getMessageKey());
        sendMessageResp.setMessageTime(System.currentTimeMillis());

        //2.发消息给同步在线端
        syncToSender(message,message);
        //3.发消息给对方在线端
        dispatchMessage(message);
        return sendMessageResp;
    }

}
