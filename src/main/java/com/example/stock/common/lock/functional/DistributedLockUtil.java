package com.example.stock.common.lock.functional;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
public class DistributedLockUtil {
    private final RedissonClient redissonClient;

    public DistributedLockUtil(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 함수형 분산 락 적용
     * @param key 락 키
     * @param waitTime 락 대기 시간
     * @param leaseTime 락 유지 시간
     * @param task 실행할 함수
     * @return task 실행 결과
     */
    public <T> T executeWithLock(String key, long waitTime, long leaseTime, Supplier<T> task) {
        RLock lock = redissonClient.getLock(key);
        boolean isLocked = false;

        try {
            isLocked = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            log.info("🔍 Redisson 락 획득 시도: key={} | 성공 여부={}", key, isLocked);
            if (!isLocked) {
                throw new RuntimeException("🔒 락을 획득할 수 없습니다. key=" + key);
            }

            return task.get(); // 🚀 전달된 함수 실행

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("🔴 락 획득 중 인터럽트 발생", e);
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("🔓 락 해제 완료: {}", key);
            }

            /*TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    *//*try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }*//*
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.info("🔓 트랜잭션 커밋 후 락 해제 완료: {}", key);
                    }
                }
            });*/
        }
    }
}

