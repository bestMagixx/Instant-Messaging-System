package com.lld.im.tcp.utils;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.pack.user.UserStatusChangeNotifyPack;
import com.lld.im.codec.proto.MessageHeader;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.ImConnectStatusEnum;
import com.lld.im.common.enums.command.UserEventCommand;
import com.lld.im.common.model.UserClientDTO;
import com.lld.im.common.model.UserSession;
import com.lld.im.tcp.publish.MqMessageProducer;
import com.lld.im.tcp.redis.RedisManager;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionSocketHolder {

    private static  final Map<UserClientDTO, NioSocketChannel> CHANNELS = new ConcurrentHashMap<>();

    public static void put(Integer appId,String userId,Integer clientType, String imei, NioSocketChannel channel){
        UserClientDTO dto = new UserClientDTO();
        dto.setAppId(appId);
        dto.setUserId(userId);
        dto.setClientType(clientType);
        dto.setImei(imei);
        CHANNELS.put(dto,channel);
    }

    public static NioSocketChannel get(Integer appId,String userId,Integer clientType,String imei){
        UserClientDTO dto = new UserClientDTO();
        dto.setAppId(appId);
        dto.setUserId(userId);
        dto.setClientType(clientType);
        dto.setImei(imei);
        return CHANNELS.get(dto);
    }

    public static List<NioSocketChannel> get(Integer appId, String id){

        Set<UserClientDTO> channelInfos = CHANNELS.keySet();
        List<NioSocketChannel> channels = new ArrayList<>();

        channelInfos.forEach(channel -> {
            if(channel.getAppId().equals(appId) && id.equals(channel.getUserId())){
                channels.add(CHANNELS.get(channel));
            }
        });
        return channels;
    }

    public static void remove(Integer appId,String userId,Integer clientType,String imei){
        UserClientDTO dto = new UserClientDTO();
        dto.setAppId(appId);
        dto.setUserId(userId);
        dto.setClientType(clientType);
        dto.setImei(imei);
        CHANNELS.remove(dto);
    }

    public static void remove(NioSocketChannel channel){
        CHANNELS.entrySet().removeIf(entity -> entity.getValue() == channel);
    }

    public static void removeUserSession(NioSocketChannel nioSocketChannel){

        String userId = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get();
        Integer appId = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.AppId)).get();
        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();

        SessionSocketHolder.remove(appId,userId,clientType,imei);

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setImei(imei);
        messageHeader.setClientType(clientType);
        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        userStatusChangeNotifyPack.setAppId(appId);
        userStatusChangeNotifyPack.setUserId(userId);
        userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
        //发送给MQ
        MqMessageProducer.sendMessage(userStatusChangeNotifyPack, messageHeader, UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());



        RedissonClient redissonClient = RedisManager.getRedissonClient();
        redissonClient.getMap(appId + Constants.RedisConstants.UserSessionConstants + userId)
                .remove(clientType + ":" + imei);
        nioSocketChannel.close();
    }

    public static void offlineUserSession(NioSocketChannel nioSocketChannel){

        String userId = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get();
        Integer appId = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.AppId)).get();
        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();


        SessionSocketHolder.remove(appId,userId,clientType,imei);
        RedissonClient redissonClient = RedisManager.getRedissonClient();
        RMap<String, String> map = redissonClient.getMap(appId + Constants.RedisConstants.UserSessionConstants + userId);
        String session = map.get(clientType.toString()+ ":" + imei);

        if(StringUtils.isNotBlank(session)){
            UserSession userSession = JSONObject.parseObject(session, UserSession.class);
            userSession.setConnectState(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
            map.put(clientType+":"+imei,JSONObject.toJSONString(userSession));
        }

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setImei(imei);
        messageHeader.setClientType(clientType);
        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        userStatusChangeNotifyPack.setAppId(appId);
        userStatusChangeNotifyPack.setUserId(userId);
        userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
        //发送给MQ
        MqMessageProducer.sendMessage(userStatusChangeNotifyPack, messageHeader, UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());


        nioSocketChannel.close();
    }

}
