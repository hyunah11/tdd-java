package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public UserPoint getUserPoint(long id) {
        return userPointTable.selectById(id);
    }

    public List<PointHistory> getUserPointHistory(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint chargePoint(long id, long amount) {
        UserPoint userPointInfo = userPointTable.selectById(id);

        if(amount <= 0) return userPointInfo;

        long now = System.currentTimeMillis();
        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, now);

        amount += userPointInfo.point();
        userPointTable.insertOrUpdate(id, amount);

        return new UserPoint(id, amount, now);
    }

    public UserPoint usePoint(long id, long amount) {
        UserPoint userPointInfo = userPointTable.selectById(id);

        if(amount <= 0) return userPointInfo;
        if(userPointInfo.point() < amount) return userPointInfo;

        long now = System.currentTimeMillis();
        pointHistoryTable.insert(id, amount, TransactionType.USE, now);

        long useResult = userPointInfo.point() - amount;
        userPointTable.insertOrUpdate(id, useResult);

        return new UserPoint(id, useResult, now);
    }
}
