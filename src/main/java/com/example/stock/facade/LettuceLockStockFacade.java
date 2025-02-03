package com.example.stock.facade;

import com.example.stock.repository.RedisLockRepository;
import com.example.stock.service.StockService;
import org.springframework.stereotype.Component;

@Component
public class LettuceLockStockFacade {

  private final RedisLockRepository redisLockRepository;
  private final StockService stockService;

  public LettuceLockStockFacade(RedisLockRepository redisLockRepository, StockService stockService) {
    this.redisLockRepository = redisLockRepository;
    this.stockService = stockService;
  }


  public void decrease(Long id, Long quantity) throws InterruptedException {
    // 락 획득을 실패하였다면, 쓰레드 sleep을 통해 100 millseconse 텀을 두고 락 획득을 재시도 한다.
    // 레디스에 갈수 있는 부하를 줄이기 위해서다.
    while (!redisLockRepository.lock(id)) {
      System.out.println("Lock 획득 실패, 재시도 중..."); //  로그 추가
      Thread.sleep(100);
    }

    // 락을 획득하였다면 다음을 수행합니다.
    try {
      stockService.decrease(id, quantity);
    } finally {
      boolean unlocked = redisLockRepository.unlock(id);
      if (!unlocked) {
        System.out.println("⚠️ Unlock 실패: " + id);
      }
    }
  }
}
