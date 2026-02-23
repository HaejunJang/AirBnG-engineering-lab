package com.airbng.common.exception;

import com.airbng.platform.common.exception.DomainException;
import com.airbng.platform.common.response.status.BaseResponseStatus;
import lombok.Getter;

@Getter
public class DistributedLockException extends DomainException {
    public DistributedLockException(BaseResponseStatus status){
        super(status);
    }
}
