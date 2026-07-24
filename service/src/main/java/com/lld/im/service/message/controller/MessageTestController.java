package com.lld.im.service.message.controller;

import com.lld.im.common.ResponseVO;
import com.lld.im.common.model.message.GroupChatMessageContent;
import com.lld.im.service.message.dao.ImMessageBodyEntity;
import com.lld.im.service.message.dao.ImMessageHistoryEntity;
import com.lld.im.service.message.dao.mapper.ImMessageBodyMapper;
import com.lld.im.service.message.dao.mapper.ImMessageHistoryMapper;
import com.lld.im.service.message.service.MessageStoreService;
import com.lld.im.service.utils.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息存储测试接口（仅用于开发测试，上线前删除）
 */
@RestController
@RequestMapping("/test/message")
public class MessageTestController {

    @Autowired
    ImMessageBodyMapper imMessageBodyMapper;

    @Autowired
    ImMessageHistoryMapper imMessageHistoryMapper;

    @Autowired
    SnowflakeIdWorker snowflakeIdWorker;

    @Autowired
    MessageStoreService messageStoreService;

    @PostMapping("/storeP2P")
    public ResponseVO storeP2PMessage(
            @RequestParam Integer appId,
            @RequestParam String fromId,
            @RequestParam String toId,
            @RequestParam String messageBody) {
        try {
            long msgKey = snowflakeIdWorker.nextId();
            long now = System.currentTimeMillis();

            // 1. 插入 messageBody
            ImMessageBodyEntity bodyEntity = new ImMessageBodyEntity();
            bodyEntity.setAppId(appId);
            bodyEntity.setMessageKey(msgKey);
            bodyEntity.setMessageBody(messageBody);
            bodyEntity.setSecurityKey("");
            bodyEntity.setMessageTime(now);
            bodyEntity.setCreateTime(now);
            bodyEntity.setExtra("");
            bodyEntity.setDelFlag(0);
            imMessageBodyMapper.insert(bodyEntity);

            // 2. 插入 messageHistory（发送方和接收方各一条）
            List<ImMessageHistoryEntity> historyList = new ArrayList<>();

            ImMessageHistoryEntity fromHistory = new ImMessageHistoryEntity();
            fromHistory.setAppId(appId);
            fromHistory.setFromId(fromId);
            fromHistory.setToId(toId);
            fromHistory.setOwnerId(fromId);
            fromHistory.setMessageKey(msgKey);
            fromHistory.setMessageTime(now);
            fromHistory.setCreateTime(now);
            historyList.add(fromHistory);

            ImMessageHistoryEntity toHistory = new ImMessageHistoryEntity();
            toHistory.setAppId(appId);
            toHistory.setFromId(fromId);
            toHistory.setToId(toId);
            toHistory.setOwnerId(toId);
            toHistory.setMessageKey(msgKey);
            toHistory.setMessageTime(now);
            toHistory.setCreateTime(now);
            historyList.add(toHistory);

            imMessageHistoryMapper.insertBatchSomeColumn(historyList);

            return ResponseVO.successResponse("messageKey=" + msgKey);
        } catch (Exception e) {
            e.printStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append(e.getClass().getName()).append(": ").append(e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                sb.append(" | caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage());
                cause = cause.getCause();
            }
            return ResponseVO.errorResponse(500, "存储失败: " + sb.toString());
        }
    }

    @PostMapping("/storeGroup")
    public ResponseVO storeGroupMessage(
            @RequestParam Integer appId,
            @RequestParam String fromId,
            @RequestParam String groupId,
            @RequestParam String messageBody) {
        try {
            GroupChatMessageContent messageContent = new GroupChatMessageContent();
            messageContent.setAppId(appId);
            messageContent.setFromId(fromId);
            messageContent.setGroupId(groupId);
            messageContent.setMessageBody(messageBody);
            messageContent.setMessageTime(System.currentTimeMillis());

            messageStoreService.storeGroupMessage(messageContent);
            return ResponseVO.successResponse("messageKey=" + messageContent.getMessageKey());
        } catch (Exception e) {
            e.printStackTrace();
            StringBuilder sb = new StringBuilder();
            sb.append(e.getClass().getName()).append(": ").append(e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                sb.append(" | caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage());
                cause = cause.getCause();
            }
            return ResponseVO.errorResponse(500, "存储失败: " + sb.toString());
        }
    }
}
