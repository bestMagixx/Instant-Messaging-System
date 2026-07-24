package com.lld.im.service.group.controller;

import com.lld.im.common.ResponseVO;
import com.lld.im.common.model.SyncReq;
import com.lld.im.service.group.model.req.*;
import com.lld.im.service.group.service.GroupMessageService;
import com.lld.im.service.group.service.ImGroupService;
import com.lld.im.service.message.model.req.SendGroupMessageReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("v1/group")
public class ImGroupController {

    @Autowired
    ImGroupService imGroupService;

    @Autowired
    GroupMessageService groupMessageService;

    @RequestMapping("/importGroup")
    public ResponseVO importGroup(@RequestBody @Validated ImportGroupReq req, Integer appId){
        req.setAppId(appId);
        return  imGroupService.importGroup(req);
    }

    @RequestMapping("/createGroup")
    public ResponseVO createGroup(@RequestBody @Validated CreateGroupReq req,Integer appId,String identifier){
        req.setAppId(appId);
        req.setOperater(identifier);
        return imGroupService.createGroup(req);
    }

    @RequestMapping("/destroyGroup")
    public ResponseVO destroyGroup(@RequestBody @Validated DestroyGroupReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        req.setOperater(identifier);
        return imGroupService.destroyGroup(req);
    }

    @RequestMapping("/transferGroup")
    public ResponseVO transferGroup(@RequestBody @Validated TransferGroupReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        req.setOperater(identifier);
        return imGroupService.transferGroup(req);
    }

    @RequestMapping("/updateGroup")
    public ResponseVO update(@RequestBody @Validated UpdateGroupReq req,Integer appId,String identifier){
        req.setAppId(appId);
        req.setOperater(identifier);
        return imGroupService.updateBaseGroupInfo(req);
    }

    @RequestMapping("/getGroupInfo")
    public ResponseVO getGroupInfo(@RequestBody @Validated GetGroupReq req,Integer appId,String identifier){
        req.setAppId(appId);
        req.setOperater(identifier);
        return imGroupService.getGroup(req);
    }

    @RequestMapping("/getGroup")
    public ResponseVO getGroup(@RequestBody @Validated String groupId, Integer appId,String identifier){
        return imGroupService.getGroup(groupId,appId);
    }

    @RequestMapping("/getJoinedGroup")
    public ResponseVO getJoinedGroup(@RequestBody @Validated GetJoinedGroupReq req, Integer appId, String identifier){
        req.setAppId(appId);
        req.setOperater(identifier);
        return imGroupService.getJoinedGroup(req);
    }

    @RequestMapping("/sendMessage")
    public ResponseVO sendMessage(@RequestBody @Validated SendGroupMessageReq req, Integer appId, String identifier){
        req.setAppId(appId);
        req.setOperater(identifier);
        return ResponseVO.successResponse(groupMessageService.send(req));
    }

    @RequestMapping("/syncJoinedGroup")
    public ResponseVO syncJoinedGroup(@RequestBody @Validated SyncReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        return imGroupService.syncJoinedGroupList(req);
    }
}
