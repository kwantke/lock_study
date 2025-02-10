package com.example.stock.common.lock.aop;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

// 어노테이선 생성
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    String key();
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    long waitTime() default 2L;
    long leaseTime() default 2L;
}
