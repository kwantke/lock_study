package com.example.stock.controller;


import com.example.stock.service.FuntionalRedissonLockStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class StockController {

    private final FuntionalRedissonLockStockService funtionalRedissonLockStockService;


    @GetMapping("/stock")
    public String stockDecrease(){

        funtionalRedissonLockStockService.decrease(1L,1L);
        return "test";
    }

}
