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
| Application Level      | 트랜잭션 간 읽기/쓰기 간섭을 제어하여 Dirty Read / Non-repeatable Read / Phantom Read 방지                                   | READ COMMITTED, REPEATABLE READ, **`SERIALIZABLE`** 설정을 통해 동시성과 일관성의 균형 조절                    |
| 비관적 락 (Pessimistic Lock)          | 트랜잭션 충돌이 “자주 발생”한다고 가정. 데이터를 읽을 때부터 락을 걸어 다른 접근 차단                                                         | @Lock(LockModeType.PESSIMISTIC_WRITE) 또는 SELECT ... FOR UPDATE 사용                       |
| 낙관적 락 (Optimistic Lock)           | 트랜잭션 충돌이 “드물다”고 가정하고, 수정 시점에 버전(version) 비교로 충돌 감지                                                         | @Version 필드 이용. 충돌 시 OptimisticLockException 발생. 동시 읽기 허용, 충돌 시 재시도 필요       |
| Named Lock (MySQL 전용)             | DB 레벨의 사용자 정의 이름 기반 락. 같은 이름을 가진 락은 동시에 1개 트랜잭션만 획득 가능                                                     | SELECT GET_LOCK('lock_name', timeout) / RELEASE_LOCK('lock_name'). 분산 환경에서도 간단하게 동기화 가능 |
| Lettuce 기반 분산락                    | Spring Data Redis 기본 클라이언트. 단일 Redis 노드 기반 락 구현 가능하지만 RedLock(분산 안정성 보장) 미지원                               | SETNX 방식 사용. 가볍지만 Redis 장애 시 위험                   |
| Redisson 기반 분산락 (RedLock 알고리즘 지원) | 멀티 Redis 노드 환경에서 분산락 구현. 자동 만료, 재시도, 페어락 등 고급 기능 제공                                                        | RLock lock = redissonClient.getLock("lockKey"); lock.tryLock(10, 5, TimeUnit.SECONDS);  |
| 분산락 (Distributed Lock)            | 여러 서버가 동일한 자원을 다룰 때 동시에 접근하지 못하게 하는 락 시스템                                                                  | Redis 기반으로 구현. Redisson이 대표적인 고신뢰 방식                                   |


### 1. Application Level
>  `Serializable` 키워드를 메서드 선언부에 넣어주면, 해당 메서드는 한개의 스레드만 접근이 가능하다.

```java
//재고 감소 메서드
@Transactional
public synchronized void decrease(Long id, Long quantity){
    // Stock 조회
    Stock stock = stockRepository.findById(id).orElseThrow();

    // 재고를 감소
    stock.decrease(quantity);

    // 갱신된 값을 저장
    stockRepository.saveAndFlush(stock);
}
```
#### 실행 결과
<img width="987" height="180" alt="synchronized_transaction_error" src="https://github.com/user-attachments/assets/2da0a33e-66f6-484b-a360-7fddb89d37e1" />

>`synchronized`를 사용했음에도 불구하고 문제를 해결하지 못합니다.<br>
>그 이유는 `@Transactionl`을 사용하면, Spring AOP 프록시 객체가 만들어지고, 원래 객체인 `stockService`의 `decrease()`의 실행이 끝나고<br>
>트랜젝션이 커밋되기 전에 다른 스레드가 데이터를 읽었기때문에 갱신 손실 문제를 해결할 수 없었던 것이다.

#### 해결방법
- 프록시 객체가 문제의 원인이었으니, `@Transactionl`을 주석 처리하면 됩니다.
<img width="1522" height="112" alt="synchronized_success" src="https://github.com/user-attachments/assets/02881ee4-f0e2-46a8-8d49-5e3611fce708" />

#### 문제점
- `synchronized`는 한 프로세스 내에서만 동시성 제어를 할 수 있다.
- 요청이 많은 경우 성능 저하가 심합니다.
- 서버가 여러 대일 경우 `갱신 손실 문제`를 해결할 수 없습니다.
- 즉, 실제 운영중인 서비스는 대부분 두 대 이상의 서버를 사용하기때문에 `sychronized`는 동시성 제어에 적합하지 않습니다.
<img width="1445" height="608" alt="lost Update Problem" src="https://github.com/user-attachments/assets/f68ada58-fe20-4080-ad74-ab6d4898cf55" />



### 2. 비관적 락(Perssimistic Lock)
- 말 그대로, `충돌이 무조건 발생한다` 라는 비관적인 전제를 가정하고, 데이터에 우선 락을 거는 방식입니다.
- 비관락은 대표적으로 `SELECT ... FOR UPDATE 문`을 통해 조회시점에 배타 락(Exclusive Lock)을 확득하고, Transaction Commit(또는 Rollback) 되는 시점에 반납하는 방식으로 동작합니다.
- `@Lock` 어노테이션을 활용하여, 비관적 락을 구현할 수 있습니다.  
```java
public interface StockRepository extends JpaRepository<Stock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) // 비관 락 설정
    @Query("""
            select s
            from Stock s where s.id = :id
            """
    )
    Stock findByIdWithPessimisticLock(Long id);
}
```
#### 동작 방식
<img width="500" height="500" alt="perssimistic_lock_process" src="https://github.com/user-attachments/assets/01bed20e-b353-4f30-84d5-0ac85dea2bdf" />

#### 실행 결과
- 테스트가 정상적으로 완료되었습니다.
<img width="1462" height="109" alt="perssimistic_success" src="https://github.com/user-attachments/assets/156e3e31-1ba3-41c9-8963-28e9a6f83698" />
쿼리를 보면 `for update`문구가 있는데, 이 부분이 락을 걸고 데이터를 가져옵니다.
<img width="1268" height="68" alt="perssimistic_lock_log" src="https://github.com/user-attachments/assets/0c7441d8-9b9a-43f5-9152-8799d33cebdf" />

#### 장단점
- 데드락(Dead Lock)이 걸릴 수 있습니다.
- 별도의 Lock을 잡기 때문에 성능 저하가 있을 수 있습니다.

#### 데드락(Dead Lock) 재현
>요약:<br>
>A는 id=1 -> id=2 순서로 잠금<br>
>B는 id=2 -> id=1 순서로 잠금<br>
>서로가 서로의 락을 기다른 순환 대기가 됨<br>
```sql
CREATE TABLE account (
  id BIGINT PRIMARY KEY,
  balance INT NOT NULL
) ENGINE=InnoDB;

INSERT INTO account(id, balance) VALUES (1, 100), (2, 100);

-- T1 시작
START TRANSACTION;
-- 1번 행을 먼저 잠금 (X 락)
SELECT * FROM account WHERE id = 1 FOR UPDATE;

-- T2 시작
START TRANSACTION;
-- 2번 행을 먼저 잠금 (X 락)
SELECT * FROM account WHERE id = 2 FOR UPDATE;

-- 이제 2번을 갱신 시도 (2번은 세션 B가 잡고 있어서 대기)
UPDATE account SET balance = balance - 10 WHERE id = 2;
-- 여기서 대기 상태 진입

-- 이제 1번 갱신 시도 (1번은 세션 A가 잡고 있어서 대기)
UPDATE account SET balance = balance - 10 WHERE id = 1;
-- 상호 대기 → InnoDB가 데드락 감지 후 한쪽을 롤백

```


### 3.낙관적 락(Optimistic Lock)
- 말 그대로, `충돌이 거의 발생하지 않을 것`이라는 낙관적인 전제를 가정합니다.
- 어떤 데이터에도 `Lock`을 걸지 않습니다.
    - 그렇기에 데드락 문제가 발생하지 않고, 위의 두가지 방법과 달리
    - `다수의 트랜잭션이 동시에 동일한 데이터에 접근할 수 있으므로 효율적인 읽기 작업을 가능하게 합니다.`
- `@Version`, `@Lock` 어노테이션을 통해 낙관적 락을 구현할 수 있습니다.
- 테이블 내부에 `버전관리`만을 위한 필드가 추가된다는 단점이 있습니다.

```java
@Entity
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    private Long quantity;

    @Version // 낙관적 락 버전 관리을 위한 필드에 @Version 추가
    private Long version; 
}

 
public interface StockRepository extends JpaRepository<Stock, Long> {
    
    @Lock(LockModeType.OPTIMISTIC) // 낙관적 락 설정
    @Query("""
            select s
            from Stock s where s.id = :id
            """
    )
    Stock findByIdWithOptimisticLock(Long id);
}

```

#### 주의 사항
- 낙관적 락은 **락 대기가 없기 때문에** 버전 충돌시 애플리케이션에서 실패시 재시도 코드를 직접 구현해야 합니다.
```java
  public void decrease(Long id, Long quantity) throws InterruptedException {
    while (true) { // 업데이트 실패했을 때 재시도를 해야함
      try {
        optimisticLockStockService.decrease(id, quantity);

        break; //  정상적으로 업데이트 되면 빠져나감
      } catch (Exception e) {
        Thread.sleep(50); // 재시도 전 50ms동안 일시 정지후 재시도
      }
    }
  }
```

#### 실행 결과
<img width="1478" height="108" alt="optimistic_success" src="https://github.com/user-attachments/assets/fc38bd14-8386-4196-8bf2-828be2ff2821" />
> 쿼리를 보면 `version` 필드도 사용하여 업데이트를 수행합니다.
<img width="1462" height="109" alt="perssimistic_success" src="https://github.com/user-attachments/assets/a3bf20e8-d721-45ea-9f6b-dad0fc763f54" />


### 3.Lettuce
- setnx 명령어를 활용하여 분산락 구현이 가능합니다.
  - `setnx`: `SET if Not eXist`의 줄임말로, 특정 key에 value값이 존재하지 않을 경우 값을 설정하는 명령어 입니다.
- spin lock 방식을 사용합니다.
  - lock을 획득하려는 스레드가 lock을 사용할 수 있느지 반복적으로 확인하면서 lock 획득을 시도합니다.
  - retry 로직을 개발자가 작성해주어야 합니다.
- Spring data redis 라이브러리를 사용하면 lettuce가 기본으로 내장되어 있습니다.

<img width="600" height="350" alt="lettuce_process" src="https://github.com/user-attachments/assets/5f70f90a-f24c-4151-9db7-151ddd7258c1" />


```java
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


@Component
public class LettuceLockStockFacade {

  private final RedisLockRepository redisLockRepository;
  private final StockService stockService;

  public LettuceLockStockFacade(RedisLockRepository redisLockRepository, StockService stockService) {
    this.redisLockRepository = redisLockRepository;
    this.stockService = stockService;
  }


  public void decrease(Long id, Long quantity) throws InterruptedException {

    // 락 획득을 실패하였다면, 쓰레드 sleep을 통해 100 ms 텀을 두고 락 획득을 재시도 한다.
    // 레디스에 갈수 있는 부하를 줄이기 위합입니다.
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
```
#### 실행 결과
<img width="1588" height="105" alt="rettuce_success" src="https://github.com/user-attachments/assets/5f040854-0786-43a0-89c7-c7a44222fa8f" />
- 구현이 간단하다는 장점이 있지만, spin lock 방식이므로 redis에 부하를 줄 수 있습니다.
- 그렇기 때문에 `Thread.sleep(100)`으로 lock 획득 재시도 간 텀을 두어야 합니다.

#### 장단점
##### 장점
- 구현이 간단하고, 속도가 빠릅니다.

##### 단점
- spin lock 방식이므로 redis 부하가 큽니다.

### 4.Redisson
- pub-sub 기반으로 Lock 구현을 제공합니다.

- lock 획득 재시도를 기본으로 제공
- `Redisson 라이브라리`를 추가해야합니다.
    - lock 관련 class를 라이브러리에서 재공해주기 때문에 별도의 repository를 작성하지 않아도 됩니다.

#### pub-sub 프로세스
- 채널을 하나 만들고 lock을 점유중인 스레다가 lock 획득을 획득하려고 대기하는 스레드에게 해제를 알려주면, 안내 받은 스레드가 lock 획득을 시도하는 방식입니다.
<img width="1486" height="306" alt="redisson_process" src="https://github.com/user-attachments/assets/9e402134-68ad-4026-8e12-bc425042873e" />

#### 구현 소스코드
> 비즈니스 로직 수행 전후로 lock을 획득하고 해제하는 로직을 작성해주어야 합니다.
```java
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
```
#### 실행 결과
<img width="1609" height="109" alt="redisson_success" src="https://github.com/user-attachments/assets/fb267c8a-d2e3-493d-8b4c-b7f11eeba4af" />

#### 장단점
##### 장점
- 멀티 서버 환경에서 안정적인 동시성 제어를 해줍니다.(분산 큐, 분산 이벤트 활용)
##### 단점
- 오버헤드가 큽 수 있습니다.
  - 이유는 단순히 Redis 명령을 보내는 클라이언트가 아니라, **분산 락/분산 자료구조/ 자동 재시도 / 연결 복구 / TTL 관리**까지 해줍니다.


