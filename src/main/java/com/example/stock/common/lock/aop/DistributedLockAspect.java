package com.example.stock.common.lock.aop;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAspect {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(com.example.stock.common.lock.aop.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String key = REDISSON_LOCK_PREFIX +
                CustomSpringELParser.getDynamicValue(signature.getParameterNames(), joinPoint.getArgs(), distributedLock.key());

        RLock rLock = redissonClient.getLock(key);

        boolean available = false;
        try {
            available = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
            log.info("🔍 Redisson 락 획득 시도: key={} | 성공 여부={}", key, available);

            if (!available) {
                log.error("🚨 락 획득 실패: {}", key);
                throw new RuntimeException("🔒 락을 획득할 수 없습니다: " + key);
            }

            Object result = aopForTransaction.proceed(joinPoint);

            log.info("✅ 트랜잭션 커밋 완료 후 락 해제 준비: {}", key);
            return result;

        } finally {
            Thread.sleep(1000);
            // 🚀 트랜잭션 커밋 후 락 해제 보장
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    /*try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }*/
                    if (rLock.isHeldByCurrentThread()) {
                        rLock.unlock();
                        log.info("🔓 트랜잭션 커밋 후 락 해제 완료: {}", key);
                    }
                }
            });
        }
    }
}

