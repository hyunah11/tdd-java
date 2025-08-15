package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
        UserPoint userPointInfo = new UserPoint(userId, 500L, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(userPointInfo);

        long useAmount = 1000L;
        UserPoint result = pointService.usePoint(userId, useAmount);

        assertThat(result.point()).isEqualTo(500L);

        // 포인트 사용 실패 시 히스토리 저장이나 포인트 업데이트가 발생하지 않아야 함
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
    }

    @Test
    void concurrentChargePoint() throws InterruptedException {
        // 동시에 여러 스레드에서 같은 사용자의 포인트를 충전하는 테스트
        long userId = 1L;
        int threadCount = 10;
        long initialPoint = 1000L;
        long chargeAmount = 100L;

        UserPoint initialUserPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(initialUserPoint);
        
        // 충전 후 포인트 설정 (매번 다른 값으로 설정)
        AtomicInteger callCount = new AtomicInteger(0);
        when(userPointTable.insertOrUpdate(eq(userId), anyLong())).thenAnswer(invocation -> {
            long newPoint = invocation.getArgument(1);
            callCount.incrementAndGet();
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
        assertThat(callCount.get()).isEqualTo(threadCount);
        
        // 각 충전마다 insertOrUpdate가 호출되었는지 확인
        verify(userPointTable, times(threadCount)).insertOrUpdate(eq(userId), anyLong());
        verify(pointHistoryTable, times(threadCount)).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    void concurrentUsePoint() throws InterruptedException {
        long userId = 2L;
        int threadCount = 5;
        long initialPoint = 1000L;
        long useAmount = 50L;

        UserPoint initialUserPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(initialUserPoint);
        
        // 사용 후 포인트 설정
        AtomicInteger callCount = new AtomicInteger(0);
        when(userPointTable.insertOrUpdate(eq(userId), anyLong())).thenAnswer(invocation -> {
            long newPoint = invocation.getArgument(1);
            callCount.incrementAndGet();
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

        // 모든 스레드 완료 대기
        latch.await();
        executor.shutdown();

        // 동시성 테스트 결과 검증
        assertThat(callCount.get()).isEqualTo(threadCount);
        
        // 각 사용마다 insertOrUpdate가 호출되었는지 확인
        verify(userPointTable, times(threadCount)).insertOrUpdate(eq(userId), anyLong());
        verify(pointHistoryTable, times(threadCount)).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
    }
}
