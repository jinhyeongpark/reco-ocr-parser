package kr.co.reco.ocr.global.error;

import java.util.HashMap;
import java.util.Map;
import kr.co.reco.ocr.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // DTO 유효성 검사 (@Valid 실패)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
        MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
            .getFieldErrors()
            .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        // COMMON400 같은 공통 에러 코드를 사용, result에 상세 에러 맵을 담아 반환
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.onFailure("COMMON400", "입력값이 유효하지 않습니다.", errors));
    }

    // 비즈니스 예외 (CustomException) 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(ApiResponse.onFailure(errorCode.name(), errorCode.getMessage()));
    }

    // DB 제약 조건 위반 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("[DataIntegrityViolationException] message: {}", ex.getMessage(), ex);

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.onFailure("COMMON409", "데이터 무결성 위반이 발생했습니다. (중복 데이터 등)"));
    }

    // 그 외 정의되지 않은 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllException(Exception ex) {
        log.error("[Internal Server Error] message: {}", ex.getMessage(), ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.onFailure("COMMON500", "서버 내부 오류가 발생했습니다."));
    }
}
