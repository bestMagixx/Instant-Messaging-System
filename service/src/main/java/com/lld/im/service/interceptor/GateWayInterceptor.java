package com.lld.im.service.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.common.BaseErrorCode;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.enums.GateWayErrorCode;
import com.lld.im.common.exception.ApplicationExceptionEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

@Component
public class GateWayInterceptor implements HandlerInterceptor {

    @Autowired
    IdentityCheck identityCheck;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 CORS 预检请求（OPTIONS），预检请求不带鉴权参数
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        //获取appId
        String appIdStr = request.getParameter("appId");
        if(StringUtils.isBlank(appIdStr)){
            resp(ResponseVO.errorResponse(GateWayErrorCode.APPID_NOT_EXIST),response);
            return false;
        }
        //获取操作人
        String identifier = request.getParameter("identifier");
        System.out.println("操作人为： " + identifier);
        if(StringUtils.isBlank(identifier)){
            resp(ResponseVO.errorResponse(GateWayErrorCode.OPERATER_NOT_EXIST),response);
            return false;
        }
        //获取签名数据
        String userSign = request.getParameter("userSign");
        System.out.println("userSign为： " + userSign);
        if(StringUtils.isBlank(userSign)){
            resp(ResponseVO.errorResponse(GateWayErrorCode.USERSIGN_NOT_EXIST),response);
            return false;
        }

        //操作人，签名和AppId是否匹配0
        ApplicationExceptionEnum applicationExceptionEnum = identityCheck.checkUserSig(identifier, appIdStr, userSign);
        if(applicationExceptionEnum != BaseErrorCode.SUCCESS){
            System.out.println("身份错误");
            resp(ResponseVO.errorResponse(applicationExceptionEnum),response);
            return false;
        }

        System.out.println("身份正确");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //请求结束后清理 ThreadLocal，避免 admin 身份在请求线程上残留，被后续请求误读
        RequestHolder.remove();
    }

    private void resp(ResponseVO responseVO, HttpServletResponse response){

        PrintWriter writer = null;
        response.setContentType("text/html; charset=utf-8");

        try{
            String resp = JSONObject.toJSONString(responseVO);
            writer = response.getWriter();
            writer.write(resp);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(writer != null){
                writer.checkError();
            }
        }

    }



}
