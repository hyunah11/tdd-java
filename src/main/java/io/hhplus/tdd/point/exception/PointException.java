package io.hhplus.tdd.point.exception;

public class PointException extends RuntimeException {
    
    public enum ErrorCode {
        INVALID_AMOUNT("잘못된 금액입니다"),
        INSUFFICIENT_POINT("포인트가 부족합니다"),
        USER_NOT_FOUND("사용자를 찾을 수 없습니다");
        
        private final String defaultMessage;
        
        ErrorCode(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }
        
        public String getDefaultMessage() {
            return defaultMessage;
        }
    }
    
    private final ErrorCode errorCode;
    
    public PointException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public PointException(ErrorCode errorCode, Object... args) {
        super(formatMessage(errorCode, args));
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public String getErrorCodeString() {
        return errorCode.name();
    }
    
    private static String formatMessage(ErrorCode errorCode, Object... args) {
        switch (errorCode) {
            case INVALID_AMOUNT:
                long amount = (Long) args[0];
                return String.format("잘못된 금액입니다: %d (0보다 큰 값이어야 합니다)", amount);
            case INSUFFICIENT_POINT:
                long userId = (Long) args[0];
                long required = (Long) args[1];
                long available = (Long) args[2];
                return String.format("사용자 ID %d의 포인트가 부족합니다. 필요: %d, 보유: %d", userId, required, available);
            case USER_NOT_FOUND:
                long notFoundUserId = (Long) args[0];
                return String.format("사용자 ID %d를 찾을 수 없습니다", notFoundUserId);
            default:
                return errorCode.getDefaultMessage();
        }
    }
    
    // 정적 팩토리 메서드들
    public static PointException invalidAmount(long amount) {
        return new PointException(ErrorCode.INVALID_AMOUNT, amount);
    }
    
    public static PointException insufficientPoint(long userId, long required, long available) {
        return new PointException(ErrorCode.INSUFFICIENT_POINT, userId, required, available);
    }
    
    public static PointException userNotFound(long userId) {
        return new PointException(ErrorCode.USER_NOT_FOUND, userId);
    }
}
