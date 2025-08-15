package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @DisplayName("보유 포인트보다 많은 금액을 사용할 수 없다")
    void usePointWithInsufficientBalance() {
        long userId = 2L;       
        UserPoint userPointInfo = new UserPoint(userId, 500L, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(userPointInfo);

        long useAmount = 1000L;
        UserPoint result = pointService.usePoint(userId, useAmount);

        assertThat(result.point()).isEqualTo(500L);

        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
    }
}
