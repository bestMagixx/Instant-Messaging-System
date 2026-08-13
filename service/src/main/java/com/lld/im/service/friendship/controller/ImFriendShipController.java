package com.lld.im.service.friendship.controller;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.model.SyncReq;
import com.lld.im.service.friendship.model.req.*;
import com.lld.im.service.friendship.service.ImFriendShipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("v1/friendShip")
public class ImFriendShipController {

    @Autowired
    ImFriendShipService imFriendShipService;

    @RequestMapping("/importFriendShip")
    public ResponseVO importFriendShip(@RequestBody @Validated ImporFriendShipReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.ImportFriendShip(req);
    }

    @RequestMapping("/addFriend")
    public ResponseVO addFriendShip(@RequestBody @Validated AddFriendReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.addFriend(req);
    }

    @RequestMapping("/updateFriend")
    public ResponseVO updateFriendShip(@RequestBody @Validated UpdateFriendReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.updateFriend(req);
    }

    @RequestMapping("/deleteFriend")
    public ResponseVO deleteFriend(@RequestBody @Validated DeleteFriendReq req, Integer appId){
        if(appId == null){
            return  ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.deleteFriend(req);
    }

    @RequestMapping("/deleteAllFriend")
    public ResponseVO deleteAllFriend(@RequestBody @Validated DeleteFriendReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.deleteAllFriend(req);
    }

    @RequestMapping("/getAllFriendShip")
    public ResponseVO getAllFriendShip(@RequestBody @Validated GetAllFriendShipReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.getAllFriendShip(req);
    }

    @RequestMapping("/getRelation")
    public ResponseVO getRelation(@RequestBody @Validated GetRelationReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.getRelation(req);
    }

    @RequestMapping("/checkFriend")
    public ResponseVO checkFriend(@RequestBody @Validated CheckFriendShipReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.checkFriendShip(req);
    }

    @RequestMapping("/addBlack")
    public ResponseVO checkFriend(@RequestBody @Validated AddFriendShipBlackReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.addBlack(req);
    }

    @RequestMapping("/syncFriendshipList")
    public ResponseVO syncFriendshipList(@RequestBody @Validated SyncReq req, Integer appId, String identifier){
        req.setAppId(appId);
        return imFriendShipService.syncFriendshipList(req);
    }

    @RequestMapping("/deleteBlack")
    public ResponseVO deleteBlack(@RequestBody @Validated DeleteBlackReq req, Integer appId){
        if(appId == null){
            return ResponseVO.AppIdIsNull();
        }
        req.setAppId(appId);
        return imFriendShipService.deleteBlack(req);
    }
}
