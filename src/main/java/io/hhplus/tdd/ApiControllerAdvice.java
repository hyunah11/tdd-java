package io.hhplus.tdd;

import io.hhplus.tdd.point.exception.PointException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiControllerAdvice extends ResponseEntityExceptionHandler {
    
    @ExceptionHandler(value = PointException.class)
    public ResponseEntity<ErrorResponse> handlePointException(PointException e) {
        HttpStatus status = getHttpStatusForErrorCode(e.getErrorCode());
        return ResponseEntity.status(status)
                .body(new ErrorResponse(e.getErrorCodeString(), e.getMessage()));
    }
    
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
    
    private HttpStatus getHttpStatusForErrorCode(PointException.ErrorCode errorCode) {
        switch (errorCode) {
            case USER_NOT_FOUND:
                //return HttpStatus.NOT_FOUND;
            case INVALID_AMOUNT:
            case INSUFFICIENT_POINT:
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }
}
