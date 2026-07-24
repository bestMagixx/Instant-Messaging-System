package com.lld.im.service.utils;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.common.constant.Constants;
import com.lld.im.common.enums.ImConnectStatusEnum;
import com.lld.im.common.model.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class UserSessionUtils {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    //1.获取用户所有的Session

    public List<UserSession> getUserSession(Integer appId,String userId){
        String userSessionKey = appId + Constants.RedisConstants.UserSessionConstants
                + userId;
        Map<Object, Object> entries =
                stringRedisTemplate.opsForHash().entries(userSessionKey);
        List<UserSession> list = new ArrayList<>();
        Collection<Object> values = entries.values();
        for(Object o : values){
            String str = (String) o;
            UserSession session = JSONObject.parseObject(str, UserSession.class);
            if(session.getConnectState() == ImConnectStatusEnum.ONLINE_STATUS.getCode()){
                list.add(session);
            }
        }

        return list;
    }

    //2.获取用户除了本端的Session

    //3.获取用户指定端的Session

    public UserSession getUserSession(Integer appId,String userId,Integer clientType,String imei){
        String userSessionKey = appId + Constants.RedisConstants.UserSessionConstants
                + userId;
        String hashKey = clientType + ":" + imei;
        Object o = stringRedisTemplate.opsForHash().get(userSessionKey, hashKey);

        UserSession session =
                JSONObject.parseObject(o.toString(), UserSession.class);

        return session;
    }
}
