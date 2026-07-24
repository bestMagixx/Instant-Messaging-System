package com.lld.im.service.group.model.req;

import com.lld.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ExitGroupReq extends RequestBase {

    @NotBlank(message = "groupId不能为空")
    private String groupId;

}
