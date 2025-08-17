package io.hhplus.tdd.point;

import io.hhplus.tdd.point.exception.PointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointService pointService;

    @BeforeEach
    void setUp() {
        // 기본 사용자 데이터 설정 - 모든 테스트에서 공통으로 사용
        UserPoint user1 = new UserPoint(1L, 1000L, System.currentTimeMillis());
        when(pointService.getUserPoint(1L)).thenReturn(user1);

        UserPoint user2 = new UserPoint(2L, 500L, System.currentTimeMillis());
        when(pointService.getUserPoint(2L)).thenReturn(user2);
    }

    @Test
    void getUserPoint() throws Exception {
        // GET 요청으로 포인트 조회 API 호출 및 응답 검증
        long userId = 1L;
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(1000L))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @Test
    void returnEmptyPointForNonExistentUser() throws Exception {
        // 존재하지 않는 사용자 ID와 빈 포인트 정보 설정
        long userId = 999L;
        UserPoint emptyUserPoint = UserPoint.empty(userId);
        when(pointService.getUserPoint(userId)).thenReturn(emptyUserPoint);

        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(0L))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @Test
    void chargeUserPoint() throws Exception {
        // PATCH 요청으로 포인트 충전 API 호출 및 응답 검증
        long userId = 1L;
        long chargeAmount = 500L;
        UserPoint updatedUserPoint = new UserPoint(userId, 1500L, System.currentTimeMillis());
        when(pointService.chargePoint(userId, chargeAmount)).thenReturn(updatedUserPoint);

        mockMvc.perform(patch("/point/{id}/charge", userId)
                .contentType("application/json")
                .content(String.valueOf(chargeAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(1500L))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @Test
    void useUserPoint() throws Exception {
         // PATCH 요청으로 포인트 사용 API 호출 및 응답 검증
        long userId = 2L;
        long useAmount = 200L;
        UserPoint updatedUserPoint = new UserPoint(userId, 300L, System.currentTimeMillis());
        when(pointService.usePoint(userId, useAmount)).thenReturn(updatedUserPoint);

        mockMvc.perform(patch("/point/{id}/use", userId)
                .contentType("application/json")
                .content(String.valueOf(useAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(300L))
                .andExpect(jsonPath("$.updateMillis").exists());
    }

    @Test
    void getUserPointHistory() throws Exception {
        // GET 요청으로 포인트 히스토리 조회 API 호출 및 응답 검증
        long userId = 1L;
        List<PointHistory> expectedHistory = List.of(
            new PointHistory(1L, userId, 1000L, TransactionType.CHARGE, System.currentTimeMillis())
        );
        when(pointService.getUserPointHistory(userId)).thenReturn(expectedHistory);
        mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId))
                .andExpect(jsonPath("$[0].amount").value(1000L))
                .andExpect(jsonPath("$[0].type").value("CHARGE"));
    }

    @Test
    void chargePointWithInvalidAmount_returnError() throws Exception {
        long userId = 1L;
        long invalidAmount = 0L;
        when(pointService.chargePoint(userId, invalidAmount))
                .thenThrow(PointException.invalidAmount(invalidAmount));

        mockMvc.perform(patch("/point/{id}/charge", userId)
                .contentType("application/json")
                .content(String.valueOf(invalidAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"));
    }

    @Test
    void chargePointForNonExistentUser_returnError() throws Exception {
        long userId = 999L;
        long amount = 100L;
        when(pointService.chargePoint(userId, amount))
                .thenThrow(PointException.userNotFound(userId));

        mockMvc.perform(patch("/point/{id}/charge", userId)
                .contentType("application/json")
                .content(String.valueOf(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void usePointWithInvalidAmount_returnError() throws Exception {
        long userId = 1L;
        long invalidAmount = -100L;
        when(pointService.usePoint(userId, invalidAmount))
                .thenThrow(PointException.invalidAmount(invalidAmount));

        mockMvc.perform(patch("/point/{id}/use", userId)
                .contentType("application/json")
                .content(String.valueOf(invalidAmount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"));
    }

    @Test
    void usePointForNonExistentUser_returnError() throws Exception {
        long userId = 999L;
        long amount = 100L;
        when(pointService.usePoint(userId, amount))
                .thenThrow(PointException.userNotFound(userId));

        mockMvc.perform(patch("/point/{id}/use", userId)
                .contentType("application/json")
                .content(String.valueOf(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void usePointWithInsufficientBalance_returnError() throws Exception {
        long userId = 2L;
        long amount = 1000L; // 보유 포인트(500)보다 큰 금액
        when(pointService.usePoint(userId, amount))
                .thenThrow(PointException.insufficientPoint(userId, amount, 500L));

        mockMvc.perform(patch("/point/{id}/use", userId)
                .contentType("application/json")
                .content(String.valueOf(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_POINT"));
    }
}