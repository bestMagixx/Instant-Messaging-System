package com.lld.im.service.group.model.req;

import com.lld.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class GetJoinedGroupReq extends RequestBase {

    @NotBlank(message = "memberId不能为空")
    private String memberId;

    private List<String> groupType;

}
