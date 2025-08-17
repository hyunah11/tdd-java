package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.point.exception.PointException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    private final ConcurrentHashMap<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private ReentrantLock lockFor(long userId) {
        return lockMap.computeIfAbsent(userId, k -> new ReentrantLock());
    }
    public UserPoint getUserPoint(long id) {
        return userPointTable.selectById(id);
    }

    public List<PointHistory> getUserPointHistory(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint chargePoint(long id, long amount) {
        if (amount <= 0) {
            throw PointException.invalidAmount(amount);
        }
        
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            UserPoint userPointInfo = userPointTable.selectById(id);
            if (userPointInfo == null) {
                throw PointException.userNotFound(id);
            }

            long now = System.currentTimeMillis();
            pointHistoryTable.insert(id, amount, TransactionType.CHARGE, now);

            long newAmount = userPointInfo.point() + amount;
            userPointTable.insertOrUpdate(id, newAmount);

            return new UserPoint(id, newAmount, now);
        } finally {
            lock.unlock();
        }
    }

    public UserPoint usePoint(long id, long amount) {
        if (amount <= 0) {
            throw PointException.invalidAmount(amount);
        }
        
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            UserPoint userPointInfo = userPointTable.selectById(id);
            if (userPointInfo == null) {
                throw PointException.userNotFound(id);
            }
            if (userPointInfo.point() < amount) {
                throw PointException.insufficientPoint(id, amount, userPointInfo.point());
            }

            long now = System.currentTimeMillis();
            pointHistoryTable.insert(id, amount, TransactionType.USE, now);

            long useResult = userPointInfo.point() - amount;
            userPointTable.insertOrUpdate(id, useResult);

            return new UserPoint(id, useResult, now);
        } finally {
            lock.unlock();
        }
    }
}
