# [인프런] 재고시스템으로 알아보는 동시성 이슈 해결방법
- 간단한 재고 관린 시스템을 구현하여 동시성 이슈가 무엇이고, 이를 처리하는 방법들을 학습합니다.
- [Inflearn Link](https://inf.run/is4v)

## 기술 스택
- Spring Boot 3.3.8
- Spring Data JPA
- Java 17
- MySQL
- Redis

## 학습 정리

### 동시성 이슈란? 
- 여러 트랜젝션이나 스레드가 동시에 같은 자원(데이터)를 접근하거나 수정할때 생기는 문제입니다.
- 예: 레이스 컨디션(Race Condition)

### 동시성 이슈 해결 방법

### 해결 방법에 대한 간략한 정의
| 방법                                | 설명                                                                                                         | 주요 사용 예시 / 특징                                                                           |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| 트랜잭션 격리 수준 (Isolation Level)      | 트랜잭션 간 읽기/쓰기 간섭을 제어하여 Dirty Read / Non-repeatable Read / Phantom Read 방지                                   | READ COMMITTED, REPEATABLE READ, **`SERIALIZABLE`** 설정을 통해 동시성과 일관성의 균형 조절                    |
| 비관적 락 (Pessimistic Lock)          | 트랜잭션 충돌이 “자주 발생”한다고 가정. 데이터를 읽을 때부터 락을 걸어 다른 접근 차단                                                         | @Lock(LockModeType.PESSIMISTIC_WRITE) 또는 SELECT ... FOR UPDATE 사용                       |
| 낙관적 락 (Optimistic Lock)           | 트랜잭션 충돌이 “드물다”고 가정하고, 수정 시점에 버전(version) 비교로 충돌 감지                                                         | @Version 필드 이용. 충돌 시 OptimisticLockException 발생. 동시 읽기 허용, 충돌 시 재시도 필요       |
| Named Lock (MySQL 전용)             | DB 레벨의 사용자 정의 이름 기반 락. 같은 이름을 가진 락은 동시에 1개 트랜잭션만 획득 가능                                                     | SELECT GET_LOCK('lock_name', timeout) / RELEASE_LOCK('lock_name'). 분산 환경에서도 간단하게 동기화 가능 |
| Lettuce 기반 분산락                    | Spring Data Redis 기본 클라이언트. 단일 Redis 노드 기반 락 구현 가능하지만 RedLock(분산 안정성 보장) 미지원                               | SETNX 또는 SET key value NX PX timeout 방식 사용. 가볍지만 Redis 장애 시 위험                   |
| Redisson 기반 분산락 (RedLock 알고리즘 지원) | 멀티 Redis 노드 환경에서 분산락 구현. 자동 만료, 재시도, 페어락 등 고급 기능 제공                                                        | RLock lock = redissonClient.getLock("lockKey"); lock.tryLock(10, 5, TimeUnit.SECONDS);  |
| 분산락 (Distributed Lock)            | 여러 서버가 동일한 자원을 다룰 때 동시에 접근하지 못하게 하는 락 시스템                                                                  | Redis 기반으로 구현. Redisson이 대표적인 고신뢰 방식                                   |


### 1. Application Level
> `Serializable` 키워드를 메서드 선언부에 넣어주면, 해당 메서드는 한개의 스레드만 접근이 가능하다.
>  
```java
//재고 감소 메서드
public synchronized void decrease(Long id, Long quantity){
    // Stock 조회
    Stock stock = stockRepository.findById(id).orElseThrow();

    // 재고를 감소
    stock.decrease(quantity);

    // 갱신된 값을 저장
    stockRepository.saveAndFlush(stock);
}


```

  




