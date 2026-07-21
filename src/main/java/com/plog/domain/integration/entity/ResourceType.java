package com.plog.domain.integration.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// link_type 컬럼을 따로 두지 않는 이유: resource_type에서 유도 가능하므로
// 둘을 같이 저장하면 모순 상태(GITHUB + NOTION_PAGE)가 표현 가능해짐.
@Getter
@RequiredArgsConstructor
public enum ResourceType {
    REPOSITORY(LinkType.GITHUB),
    FIGMA_FILE(LinkType.FIGMA),
    NOTION_PAGE(LinkType.NOTION),
    NOTION_DATABASE(LinkType.NOTION);

    private final LinkType linkType;
}
