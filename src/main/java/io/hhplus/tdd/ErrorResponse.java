package io.hhplus.tdd;

public record ErrorResponse(
        String errorCode,
        String message
) {
}
