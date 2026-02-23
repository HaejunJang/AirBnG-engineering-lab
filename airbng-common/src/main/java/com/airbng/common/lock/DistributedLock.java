package com.airbng.common.lock;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    //락 이름
    String key();

    //락 획득 대기 시간 단위
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    //락 획득 최대 대기
    long waitTime() default 200L;

    //락 점유 시간
    long leaseTime() default 3_000L;
}
