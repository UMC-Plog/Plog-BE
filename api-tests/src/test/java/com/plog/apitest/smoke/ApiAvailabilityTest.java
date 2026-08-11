package com.plog.apitest.smoke;

import com.plog.apitest.support.ApiTestConfig;
import com.plog.apitest.support.client.AvailabilityApiClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * API 가용성 검사
 *
 * APIs:
 * - GET /v3/api-docs
 */
@Tag("live")
class ApiAvailabilityTest {

    /**
     * API 상태 조회
     *
     * <p>API: {@code GET /v3/api-docs}</p>
     * <p>인증: 불필요</p>
     * <p>요청: 없음</p>
     * <p>요청 Body: 없음</p>
     * <p>성공: HTTP 200</p>
     *
     * <p>응답 Body: 검증하지 않음</p>
     *
     * <p>핵심 계약:</p>
     * <ul>
     *   <li>API 서버가 상태 확인 요청에 정상적으로 응답한다.</li>
     * </ul>
     *
     * <p>상태 변경: 없음</p>
     */
    @Nested
    class API_상태_조회 {

        @Test
        void API_서버가_요청에_응답한다() {
            new AvailabilityApiClient(ApiTestConfig.baseUrlFromEnvironment()).health().then()
                    .statusCode(200);
        }
    }
}
