package com.lld.im.service.group.controller;

import com.lld.im.common.ResponseVO;
import com.lld.im.service.group.model.req.AddGroupMemberReq;
import com.lld.im.service.group.model.req.GroupMemberDto;
import com.lld.im.service.group.model.req.ImportGroupMemberReq;
import com.lld.im.service.group.model.resp.AddMemberResp;
import com.lld.im.service.group.model.resp.GetRoleInGroupResp;
import com.lld.im.service.group.service.ImGroupMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("v1/group/member")
public class ImGroupMemberController {

    @Autowired
    ImGroupMemberService imGroupMemberService;

    @RequestMapping("/importGroupMember")
    public ResponseVO importGroupMember(@RequestBody @Validated ImportGroupMemberReq req,Integer appId,String identifier){
        req.setAppId(appId);
        req.setOperater(identifier);
        return imGroupMemberService.importGroupMember(req);
    }

    @RequestMapping("/add")
    public ResponseVO addMember(@RequestBody @Validated AddGroupMemberReq req,Integer appId,String identifier){
        req.setAppId(appId);
        req.setOperater(identifier);
        return imGroupMemberService.addMember(req);
    }
}
