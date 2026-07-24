package com.lld.im.service.friendship.model.req;

import com.lld.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DeleteBlackReq extends RequestBase {

    @NotBlank(message = "fromId不能为空")
    private String fromId;

    @NotBlank(message = "toId不能为为空")
    private String toId;

}
