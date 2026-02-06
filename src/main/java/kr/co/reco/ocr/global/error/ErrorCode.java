package kr.co.reco.ocr.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 공통 에러
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 유효하지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),

    // OCR 에러
    OCR_PARSING_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "OCR 텍스트에서 필수 정보를 추출할 수 없습니다."),
    SAMPLE_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 샘플 파일을 찾을 수 없습니다."),

    // 데이터 조회 에러
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 ID의 계량 티켓을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
