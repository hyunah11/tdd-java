package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.point.exception.PointException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointService pointService;

    @Test
    void getUserPoint() {
        // 유저 포인트 조회 서비스 호출
        long userId = 1L;
        UserPoint expectedUserPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(expectedUserPoint);

        UserPoint result = pointService.getUserPoint(userId);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(1000L);

        verify(userPointTable).selectById(userId);
    }

    @Test
    void chargePoint() {
        // 충전할 사용자 정보와 충전 후 예상 결과 검증
        long userId = 1L;     
        UserPoint userPointInfo = new UserPoint(userId, 1000L, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, 1500L, System.currentTimeMillis());
        
        when(userPointTable.selectById(userId)).thenReturn(userPointInfo);
        when(userPointTable.insertOrUpdate(userId, 1500L)).thenReturn(updatedUserPoint);

        long chargeAmount = 500L;
        UserPoint result = pointService.chargePoint(userId, chargeAmount);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(1500L);

        verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
        verify(userPointTable).insertOrUpdate(eq(userId), eq(1500L));
    }

    @Test
    void chargeNegativePoint() {
        long userId = 1L;
        long invalidAmount = -500L;

        // 잘못된 금액으로 인한 예외 발생 검증
        assertThatThrownBy(() -> pointService.chargePoint(userId, invalidAmount))
                .isInstanceOf(PointException.class)
                .hasFieldOrPropertyWithValue("errorCode", PointException.ErrorCode.INVALID_AMOUNT);

        // 포인트 충전 실패 시 히스토리 저장이나 포인트 업데이트가 발생하지 않아야 함
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
    }

    @Test
    void usePoint() {
        // 포인트를 사용할 사용자 정보와 사용 후 예상 결과 검증
        long userId = 2L;  
        UserPoint userPointInfo = new UserPoint(userId, 500L, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, 300L, System.currentTimeMillis());
        
        when(userPointTable.selectById(userId)).thenReturn(userPointInfo);
        when(userPointTable.insertOrUpdate(eq(userId), eq(300L))).thenReturn(updatedUserPoint);

        long useAmount = 200L;
        UserPoint result = pointService.usePoint(userId, useAmount);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(300L);

        verify(pointHistoryTable).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
        verify(userPointTable).insertOrUpdate(eq(userId), eq(300L));
    }

    @Test
    void usePointWithInsufficientBalance() {
        // 잔액 부족 상황 검증 (보유 포인트 500, 사용하려는 금액 1000)
        long userId = 2L;
        long useAmount = 1000L;
        UserPoint userPointInfo = new UserPoint(userId, 500L, System.currentTimeMillis());
        
        when(userPointTable.selectById(userId)).thenReturn(userPointInfo);

        // 포인트 부족으로 인한 예외 발생 검증
        assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
                .isInstanceOf(PointException.class)
                .hasFieldOrPropertyWithValue("errorCode", PointException.ErrorCode.INSUFFICIENT_POINT);

        // 포인트 사용 실패 시 히스토리 저장이나 포인트 업데이트가 발생하지 않아야 함
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
    }

    @Test
    void concurrentChargePoint() throws InterruptedException {
        long userId = 1L;
        int threadCount = 10;
        long initialPoint = 1000L;
        long chargeAmount = 100L;
        long expectedFinalPoint = initialPoint + (chargeAmount * threadCount);

        AtomicLong currentPoint = new AtomicLong(initialPoint);
        
        // selectById 호출 시 현재 포인트를 반환하도록 Mock 설정
        when(userPointTable.selectById(userId)).thenAnswer(invocation -> {
            return new UserPoint(userId, currentPoint.get(), System.currentTimeMillis());
        });
        
        // 충전 후 포인트 설정
        when(userPointTable.insertOrUpdate(eq(userId), anyLong())).thenAnswer(invocation -> {
            long newPoint = invocation.getArgument(1);
            currentPoint.set(newPoint);
            return new UserPoint(userId, newPoint, System.currentTimeMillis());
        });

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 여러 스레드에서 동시에 포인트 충전 실행
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await();
        executor.shutdown();

        // 동시성 테스트 결과 검증
        assertThat(currentPoint.get()).isEqualTo(expectedFinalPoint);

        verify(userPointTable, times(threadCount)).insertOrUpdate(eq(userId), anyLong());
        verify(pointHistoryTable, times(threadCount)).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    void concurrentUsePoint() throws InterruptedException {
        long userId = 2L;
        int threadCount = 5;
        long initialPoint = 1000L;
        long useAmount = 50L;
        long expectedFinalPoint = initialPoint - (useAmount * threadCount);

        AtomicLong currentPoint = new AtomicLong(initialPoint);

        when(userPointTable.selectById(userId)).thenAnswer(invocation -> {
            return new UserPoint(userId, currentPoint.get(), System.currentTimeMillis());
        });
        
        // 사용 후 포인트 설정 - 각 호출마다 차감된 포인트를 반환
        when(userPointTable.insertOrUpdate(eq(userId), anyLong())).thenAnswer(invocation -> {
            long newPoint = invocation.getArgument(1);
            currentPoint.set(newPoint);
            return new UserPoint(userId, newPoint, System.currentTimeMillis());
        });

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 여러 스레드에서 동시에 포인트 사용 실행
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(currentPoint.get()).isEqualTo(expectedFinalPoint);

        verify(userPointTable, times(threadCount)).insertOrUpdate(eq(userId), anyLong());
        verify(pointHistoryTable, times(threadCount)).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
    }

    @Test
    void concurrentMixedOperations() throws InterruptedException {
        // 동시에 충전과 사용이 일어나는 복합 시나리오 테스트
        long userId = 3L;
        int chargeThreadCount = 3;
        int useThreadCount = 2;
        long initialPoint = 500L;
        long chargeAmount = 100L;
        long useAmount = 50L;

        UserPoint initialUserPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(initialUserPoint);
        
        // 포인트 변경 추적 - 호출 횟수만 확인
        AtomicInteger callCount = new AtomicInteger(0);
        when(userPointTable.insertOrUpdate(eq(userId), anyLong())).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            return new UserPoint(userId, invocation.getArgument(1), System.currentTimeMillis());
        });

        CountDownLatch latch = new CountDownLatch(chargeThreadCount + useThreadCount);
        ExecutorService executor = Executors.newFixedThreadPool(chargeThreadCount + useThreadCount);

        // 충전 스레드들 시작
        for (int i = 0; i < chargeThreadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 사용 스레드들 시작
        for (int i = 0; i < useThreadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await();
        executor.shutdown();

        // 모든 작업이 실행되었는지 확인
        assertThat(callCount.get()).isEqualTo(chargeThreadCount + useThreadCount);
        
        // 충전과 사용이 각각 예상 횟수만큼 호출되었는지 확인
        verify(pointHistoryTable, times(chargeThreadCount)).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
        verify(pointHistoryTable, times(useThreadCount)).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
        verify(userPointTable, times(chargeThreadCount + useThreadCount)).insertOrUpdate(eq(userId), anyLong());
    }

    @Test
    void concurrentDifferentUsers() throws InterruptedException {
        // 서로 다른 사용자에 대한 동시 작업 테스트 (Lock이 분리되어야 함)
        long user1Id = 10L;
        long user2Id = 20L;
        int threadCount = 5;
        long initialPoint = 1000L;
        long amount = 100L;

        AtomicLong user1Point = new AtomicLong(initialPoint);
        AtomicLong user2Point = new AtomicLong(initialPoint);

        when(userPointTable.selectById(eq(user1Id))).thenAnswer(invocation ->
                new UserPoint(user1Id, user1Point.get(), System.currentTimeMillis())
        );
        when(userPointTable.selectById(eq(user2Id))).thenAnswer(invocation ->
                new UserPoint(user2Id, user2Point.get(), System.currentTimeMillis())
        );
        
        when(userPointTable.insertOrUpdate(eq(user1Id), anyLong())).thenAnswer(invocation -> {
            long newPoint = invocation.getArgument(1);
            user1Point.set(newPoint);
            return new UserPoint(user1Id, newPoint, System.currentTimeMillis());
        });
        
        when(userPointTable.insertOrUpdate(eq(user2Id), anyLong())).thenAnswer(invocation -> {
            long newPoint = invocation.getArgument(1);
            user2Point.set(newPoint);
            return new UserPoint(user2Id, newPoint, System.currentTimeMillis());
        });

        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);

        // user1에 대한 충전 스레드들
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.chargePoint(user1Id, amount);
                } finally {
                    latch.countDown();
                }
            });
        }

        // user2에 대한 사용 스레드들
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    pointService.usePoint(user2Id, amount);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long expectedUser1Point = initialPoint + (amount * threadCount);
        long expectedUser2Point = initialPoint - (amount * threadCount);
        
        assertThat(user1Point.get()).isEqualTo(expectedUser1Point);
        assertThat(user2Point.get()).isEqualTo(expectedUser2Point);

        verify(pointHistoryTable, times(threadCount)).insert(eq(user1Id), eq(amount), eq(TransactionType.CHARGE), anyLong());
        verify(pointHistoryTable, times(threadCount)).insert(eq(user2Id), eq(amount), eq(TransactionType.USE), anyLong());
    }

    @Test
    void testLockIsolation() throws InterruptedException {
        // 한 사용자의 작업이 다른 사용자의 작업을 차단하지 않아야 함
        long user1Id = 30L;
        long user2Id = 40L;
        long initialPoint = 1000L;
        long amount = 100L;

        UserPoint user1Initial = new UserPoint(user1Id, initialPoint, System.currentTimeMillis());
        UserPoint user2Initial = new UserPoint(user2Id, initialPoint, System.currentTimeMillis());
        
        when(userPointTable.selectById(user1Id)).thenReturn(user1Initial);
        when(userPointTable.selectById(user2Id)).thenReturn(user2Initial);

        AtomicLong user1Point = new AtomicLong(initialPoint);
        AtomicLong user2Point = new AtomicLong(initialPoint);
        
        when(userPointTable.insertOrUpdate(eq(user1Id), anyLong())).thenAnswer(invocation -> {
            long newPoint = invocation.getArgument(1);
            user1Point.set(newPoint);
            // user1 작업 중에 약간의 지연을 주어 user2 작업과 겹치도록 함
            Thread.sleep(10);
            return new UserPoint(user1Id, newPoint, System.currentTimeMillis());
        });
        
        when(userPointTable.insertOrUpdate(eq(user2Id), anyLong())).thenAnswer(invocation -> {
            long newPoint = invocation.getArgument(1);
            user2Point.set(newPoint);
            return new UserPoint(user2Id, newPoint, System.currentTimeMillis());
        });

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        long startTime = System.currentTimeMillis();
        
        // user1 충전 (지연이 있는 작업)
        executor.submit(() -> {
            try {
                pointService.chargePoint(user1Id, amount);
            } finally {
                latch.countDown();
            }
        });

        // user2 사용 (즉시 실행되는 작업)
        executor.submit(() -> {
            try {
                pointService.usePoint(user2Id, amount);
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        // 두 작업이 모두 성공적으로 완료되었는지 확인
        assertThat(user1Point.get()).isEqualTo(initialPoint + amount);
        assertThat(user2Point.get()).isEqualTo(initialPoint - amount);
        
        // Lock이 분리되어 있다면 user2 작업이 user1 작업을 기다리지 않고 
        // 동시에 실행될 수 있어야 함 (전체 실행 시간이 user1의 지연 시간보다 짧아야 함)
        long totalTime = endTime - startTime;
        assertThat(totalTime).isLessThan(50); // user1의 지연 시간(10ms)보다 훨씬 짧아야 함

        verify(pointHistoryTable).insert(eq(user1Id), eq(amount), eq(TransactionType.CHARGE), anyLong());
        verify(pointHistoryTable).insert(eq(user2Id), eq(amount), eq(TransactionType.USE), anyLong());
    }
}
