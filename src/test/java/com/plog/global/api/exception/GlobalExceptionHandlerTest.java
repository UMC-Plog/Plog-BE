package com.plog.global.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.global.api.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 첨부_유니크_위반은_409로_변환된다() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_post_attachment_file\"");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("FILE_ALREADY_ATTACHED");
    }

    @Test
    void 다른_무결성_위반은_되던지지_않고_500_봉투를_유지한다() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_project_member\"");

        // 되던지면 catch-all 을 건너뛰고 컨테이너로 빠져나가 /error 기본 바디가 나간다.
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("COMMON500");
    }
}
