package com.plog.domain.user.entity;

/**
 * 프로필 아바타 프리셋. 이미지 파일은 프론트가 번들 자산으로 소유하고, 백엔드는 어떤 프리셋인지 코드만 저장한다.
 * 값·개수(8종)는 프론트 자산과 1:1로 맞춰야 하는 계약이다. 커스텀 이미지 업로드는 기획상 없다.
 */
public enum ProfilePreset {
    OTTER, PENGUIN, FROG, KOALA, PANDA, SMILEY, GHOST, TIGER
}
