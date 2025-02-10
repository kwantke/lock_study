package com.example.stock.service;

import com.example.stock.common.lock.aop.DistributedLock;
import com.example.stock.domain.Stock;
import com.example.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@RequiredArgsConstructor
@Service
public class AopDistributedLockStockService {

    private final StockRepository stockRepository;



    @DistributedLock(key = "#id")
    //@DistributedLock(key = "#model.getName().concat('-').concat(#model.getShipmentOrderNumber())")
    public void decrease(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithAopDistributedLock(id).orElseThrow();
        stock.decrease(quantity);
        stockRepository.save(stock);
    }
}
