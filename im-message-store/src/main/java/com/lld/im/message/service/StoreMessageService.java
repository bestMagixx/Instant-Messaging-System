package com.lld.im.message.service;


import com.lld.im.common.model.message.GroupChatMessageContent;
import com.lld.im.common.model.message.MessageContent;
import com.lld.im.message.dao.ImGroupMessageHistoryEntity;
import com.lld.im.message.dao.ImMessageBodyEntity;
import com.lld.im.message.dao.ImMessageHistoryEntity;
import com.lld.im.message.dao.mapper.ImGroupMessageHistoryMapper;
import com.lld.im.message.dao.mapper.ImMessageBodyMapper;
import com.lld.im.message.dao.mapper.ImMessageHistoryMapper;
import com.lld.im.message.model.DoStoreGroupMessageDto;
import com.lld.im.message.model.DoStoreP2PMessageDto;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class StoreMessageService {

    @Autowired
    ImMessageHistoryMapper imMessageHistoryMapper;

    @Autowired
    ImMessageBodyMapper imMessageBodyMapper;

    @Autowired
    ImGroupMessageHistoryMapper imGroupMessageHistoryMapper;

    @Transactional
    public void doStoreP2PMessage(DoStoreP2PMessageDto dto){
        imMessageBodyMapper.insert(dto.getMessageBodyEntity());
        List<ImMessageHistoryEntity> imMessageHistoryEntities = extractToP2PMessageHistory(dto.getMessageContent(), dto.getMessageBodyEntity());
        imMessageHistoryMapper.insertBatchSomeColumn(imMessageHistoryEntities);
    }

    public List<ImMessageHistoryEntity> extractToP2PMessageHistory(MessageContent messageContent,
                                                                   ImMessageBodyEntity imMessageBodyEntity){
        List<ImMessageHistoryEntity> list = new ArrayList<>();
        ImMessageHistoryEntity fromHistory = new ImMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent,fromHistory);
        fromHistory.setOwnerId(messageContent.getFromId());
        fromHistory.setMessageKey(imMessageBodyEntity.getMessageKey());
        fromHistory.setCreateTime(System.currentTimeMillis());
        fromHistory.setSequence(messageContent.getMessageSequence());

        ImMessageHistoryEntity toHistory = new ImMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent,toHistory);
        toHistory.setOwnerId(messageContent.getToId());
        toHistory.setMessageKey(imMessageBodyEntity.getMessageKey());
        toHistory.setCreateTime(System.currentTimeMillis());
        toHistory.setSequence(messageContent.getMessageSequence());

        list.add(fromHistory);
        list.add(toHistory);
        return list;
    }

    @Transactional
    public void doStoreGroupMessage(DoStoreGroupMessageDto dto){
        imMessageBodyMapper.insert(dto.getImMessageBodyEntity());
        ImGroupMessageHistoryEntity imGroupMessageHistoryEntity =
                extractToGroupMessageHistory(dto.getGroupChatMessageContent(), dto.getImMessageBodyEntity());
        imGroupMessageHistoryMapper.insert(imGroupMessageHistoryEntity);
    }

    private ImGroupMessageHistoryEntity extractToGroupMessageHistory(GroupChatMessageContent
                                                                             messageContent , ImMessageBodyEntity messageBodyEntity){
        ImGroupMessageHistoryEntity result = new ImGroupMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent,result);
        result.setGroupId(messageContent.getGroupId());
        result.setMessageKey(messageBodyEntity.getMessageKey());
        result.setCreateTime(System.currentTimeMillis());
        return result;
    }

}
