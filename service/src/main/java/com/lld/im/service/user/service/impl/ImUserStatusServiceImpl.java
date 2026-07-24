package com.lld.im.service.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.pack.user.UserCustomStatusChangeNotifyPack;
import com.lld.im.codec.pack.user.UserStatusChangeNotifyPack;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.command.Command;
import com.lld.im.common.enums.command.UserEventCommand;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.common.model.UserSession;
import com.lld.im.service.friendship.service.ImFriendShipService;
import com.lld.im.service.user.model.UserStatusChangeNotifyContent;
import com.lld.im.service.user.model.req.PullFriendOnlineStatusReq;
import com.lld.im.service.user.model.req.PullUserOnlineStatusReq;
import com.lld.im.service.user.model.req.SetUserCustomerStatusReq;
import com.lld.im.service.user.model.req.SubscribeUserOnlineStatusReq;
import com.lld.im.service.user.model.resp.UserOnlineStatusResp;
import com.lld.im.service.user.service.ImUserStatusService;
import com.lld.im.service.utils.MessageProducer;
import com.lld.im.service.utils.UserSessionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description:
 * @author: lld
 * @version: 1.0
 */
@Service
public class ImUserStatusServiceImpl implements ImUserStatusService {

    @Autowired
    UserSessionUtils userSessionUtils;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    ImFriendShipService imFriendService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public void processUserOnlineStatusNotify(UserStatusChangeNotifyContent content) {

        List<UserSession> userSession = userSessionUtils.getUserSession(content.getAppId(), content.getUserId());
        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        BeanUtils.copyProperties(content,userStatusChangeNotifyPack);
        userStatusChangeNotifyPack.setClient(userSession);
        //发送给自己的同步端
        syncSender(userStatusChangeNotifyPack,content.getUserId(),
                content, UserEventCommand.USER_ONLINE_STATUS_CHANGE_NOTIFY_SYNC);
        //发送给好友与订阅了自己的人
        dispatcher(userStatusChangeNotifyPack,content.getUserId(),
                content.getAppId(),UserEventCommand.USER_ONLINE_STATUS_CHANGE_NOTIFY);
    }



    private void syncSender(Object pack, String userId, ClientInfo clientInfo, Command command){
        messageProducer.sendToUserExceptClient(userId,
               command,
                pack,clientInfo);
    }

    private void dispatcher(Object pack, String userId, Integer appId, Command command){
        List<String> allFriendId = imFriendService.getAllFriendId(userId, appId);
        for (String fid : allFriendId) {
            messageProducer.sendToUser(fid,command,
                    pack,appId);
        }

        //发送给临时订阅的人
        String userKey = appId + ":" + Constants.RedisConstants.subscribe + userId;
        Set<Object> keys = stringRedisTemplate.opsForHash().keys(userKey);
        for (Object key : keys) {
            String filed = (String) key;
            Long expire = Long.valueOf((String) stringRedisTemplate.opsForHash().get(userKey, filed));
            if(expire > 0 && expire > System.currentTimeMillis()){
                messageProducer.sendToUser(filed,UserEventCommand.USER_ONLINE_STATUS_CHANGE_NOTIFY,
                        pack,appId);
            }else{
                stringRedisTemplate.opsForHash().delete(userKey,filed);
            }
        }
    }

    
    /**
     * @description: 
     * @param
     * @return void
     * @author lld 
     */
    @Override
    public void subscribeUserOnlineStatus(SubscribeUserOnlineStatusReq req) {
        // A
        // Z
        // A - B C D
        // C：A Z F
        //hash
        // B - [A:xxxx,C:xxxx]
        // C - []
        // D - []
        Long subExpireTime = 0L;
        if(req != null && req.getSubTime() > 0){
            subExpireTime = System.currentTimeMillis() + req.getSubTime();
        }

        for (String beSubUserId : req.getSubUserId()) {
            String userKey = req.getAppId() + ":" + Constants.RedisConstants.subscribe + ":" + beSubUserId;
            stringRedisTemplate.opsForHash().put(userKey,req.getOperater(),subExpireTime.toString());
        }
    }

    /**
     * @description: 设置自定义状态
     * @param
     * @return void
     * @author lld 
     */
    @Override
    public void setUserCustomerStatus(SetUserCustomerStatusReq req) {
        UserCustomStatusChangeNotifyPack pack = new UserCustomStatusChangeNotifyPack();
        pack.setCustomStatus(req.getCustomStatus());
        pack.setCustomText(req.getCustomText());
        pack.setUserId(req.getUserId());
        stringRedisTemplate.opsForValue().set(req.getAppId()
                +":"+ Constants.RedisConstants.userCustomerStatus + ":" + req.getUserId()
        ,JSONObject.toJSONString(pack));

        syncSender(pack,
                req.getUserId(),new ClientInfo(req.getAppId(),req.getClientType(),req.getImei()),
                UserEventCommand.USER_CUSTOM_STATUS_CHANGE_NOTIFY_SYNC);
        dispatcher(pack,req.getUserId(),req.getAppId(),UserEventCommand.USER_CUSTOM_STATUS_CHANGE_NOTIFY);
    }

    @Override
    public Map<String, UserOnlineStatusResp> queryFriendOnlineStatus(PullFriendOnlineStatusReq req) {

        List<String> allFriendId = imFriendService.getAllFriendId(req.getOperater(), req.getAppId());
        return getUserOnlineStatus(allFriendId,req.getAppId());
    }

    @Override
    public Map<String, UserOnlineStatusResp> queryUserOnlineStatus(PullUserOnlineStatusReq req) {
        return getUserOnlineStatus(req.getUserList(),req.getAppId());
    }

    private Map<String, UserOnlineStatusResp> getUserOnlineStatus(List<String> userId,Integer appId){
        
        Map<String, UserOnlineStatusResp> result = new HashMap<>(userId.size());
        for (String uid : userId) {

            UserOnlineStatusResp resp = new UserOnlineStatusResp();
            List<UserSession> userSession = userSessionUtils.getUserSession(appId, uid);
            resp.setSession(userSession);
            String userKey = appId + ":" + Constants.RedisConstants.userCustomerStatus + ":" + uid;
            String s = stringRedisTemplate.opsForValue().get(userKey);
            if(StringUtils.isNotBlank(s)){
                JSONObject parse = (JSONObject) JSON.parse(s);
                resp.setCustomText(parse.getString("customText"));
                resp.setCustomStatus(parse.getInteger("customStatus"));
            }
            result.put(uid,resp);
        }
        return result;
    }

}
