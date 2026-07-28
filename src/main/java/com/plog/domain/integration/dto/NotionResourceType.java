package com.plog.domain.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Notion 후보 조회와 등록 요청이 공통으로 사용하는 리소스 종류다. */
@Schema(description = "Notion 수집 대상 종류", allowableValues = {"PAGE", "DATA_SOURCE"})
public enum NotionResourceType {
    PAGE,
    DATA_SOURCE
}
