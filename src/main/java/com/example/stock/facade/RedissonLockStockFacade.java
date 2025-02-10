package com.example.stock.facade;

import com.example.stock.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedissonLockStockFacade {
  private RedissonClient redissonClient;
  private StockService stockService;

  public RedissonLockStockFacade(RedissonClient redissonClient, StockService stockService) {
    this.redissonClient = redissonClient;
    this.stockService = stockService;
  }

  public void decrease(Long id, Long quantity) {
    RLock rLock = redissonClient.getLock(id.toString());

    try {
      boolean available = rLock.tryLock(10, 1, TimeUnit.SECONDS);
      log.info("🔍 Redisson 락 획득 시도: key={} | 성공 여부={}", id, available);
      if (!available) {
        log.error("🚨 락 획득 실패: {}", id);
        return;
      }

      stockService.decrease(id, quantity);
      log.info("✅ 트랜잭션 커밋 완료 후 락 해제 준비: {}", id);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      if (rLock.isHeldByCurrentThread()) {
        rLock.unlock();
        log.info("🔓 락 해제 완료: {}", id);
      }
    }
  }
}

