package com.example.stock.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisLockRepository {
  private RedisTemplate<String, String> redisTemplate;

  public RedisLockRepository(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public Boolean lock(Long key) {
    try {
      Boolean result = redisTemplate
              .opsForValue()
              .setIfAbsent(generateKey(key), "lock", Duration.ofMillis(3_000));
      System.out.println("🔹 Lock 획득 상태: " + result);
      return result;
    } catch (Exception e) {
      System.err.println("⚠️ Redis 연결 오류: " + e.getMessage());
      return false;
    }
  }

  public Boolean unlock(Long key) {
    return redisTemplate.delete(generateKey(key));
  }
  private String generateKey(Long key) {
    return key.toString();
  }
}

