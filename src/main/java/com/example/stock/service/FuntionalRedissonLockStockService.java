package com.example.stock.service;

import com.example.stock.common.lock.functional.DistributedLockUtil;
import com.example.stock.domain.Stock;
import com.example.stock.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class FuntionalRedissonLockStockService {

    private final DistributedLockUtil lockUtil;
    private final StockRepository stockRepository;


    public FuntionalRedissonLockStockService(DistributedLockUtil lockUtil, StockRepository stockRepository) {
        this.lockUtil = lockUtil;
        this.stockRepository = stockRepository;
    }


    public void decrease(Long productId, Long quantity) {
        String lockKey = "stock:" + productId;

        lockUtil.executeWithLock(lockKey, 4, 2, () -> {

            //Transactional 을 락으로 감쌓는다
            decreaseStock(productId, quantity);
            return null;
        });
    }

    @Transactional
    public void decreaseStock(Long productId, Long quantity) {
        Stock stock = stockRepository.findByIdWithAopDistributedLock(productId)
                .orElseThrow(() -> new RuntimeException("재고 없음"));

        if (stock.getQuantity() < quantity) {
            throw new RuntimeException("재고 부족");
        }

        stock.decrease(quantity);
        stockRepository.save(stock);
        log.info("✅ 트랜잭션 커밋 완료 후 락 해제 준비");
    }
}
