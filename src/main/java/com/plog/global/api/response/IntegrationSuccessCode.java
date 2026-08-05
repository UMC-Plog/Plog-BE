package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IntegrationSuccessCode implements BaseCode {

    AUTHORIZATION_URL_ISSUED(HttpStatus.OK, "INTEGRATION001", "외부 연동 인증 URL을 발급했습니다."),
    INTEGRATION_CONNECTED(HttpStatus.OK, "INTEGRATION002", "외부 연동을 완료했습니다."),
    INTEGRATION_DISCONNECTED(HttpStatus.OK, "INTEGRATION003", "외부 연동을 해제했습니다."),
    INTEGRATION_RESOURCES_RETRIEVED(HttpStatus.OK, "INTEGRATION004", "외부 연동 리소스를 조회했습니다."),
    INTEGRATION_RESOURCE_REGISTERED(HttpStatus.CREATED, "INTEGRATION005", "외부 연동 리소스를 등록했습니다."),
    INTEGRATION_DATA_COLLECTION_ACCEPTED(HttpStatus.ACCEPTED, "INTEGRATION006", "외부 연동 데이터 수집 요청을 접수했습니다."),
    ACTOR_MAPPINGS_RETRIEVED(HttpStatus.OK, "INTEGRATION019", "프로젝트 멤버 외부 계정 매핑을 조회했습니다."),
    ACTOR_MAPPING_SAVED(HttpStatus.OK, "INTEGRATION020", "현재 멤버의 외부 계정 매핑을 저장했습니다."),
    ACTOR_MAPPING_REMOVED(HttpStatus.OK, "INTEGRATION021", "현재 멤버의 외부 계정 매핑을 해제했습니다."),
    GOOGLE_PICKER_ACCESS_TOKEN_ISSUED(HttpStatus.OK, "INTEGRATION022", "Google Picker용 access token을 발급했습니다."),
    INTEGRATION_RESOURCE_REMOVED(HttpStatus.OK, "INTEGRATION023", "외부 연동 리소스를 제거했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
