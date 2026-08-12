package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 운영에서 리포트 PDF가 통째로 실패한 원인을 고정한다.
 * <p>
 * fonts-noto-cjk 가 깔아주는 NotoSansCJK-Regular.ttc 는 CFF 아웃라인 기반이라 glyf 테이블이 없다.
 * 파일은 멀쩡히 존재해서 기동 검증은 "확인 완료"를 찍었지만, 정작 렌더링 시점에
 * PDFBox TTFSubsetter 가 "OTF fonts do not have a glyf table" 로 터졌다.
 * 파일 존재만으로는 못 걸러내는 종류의 실패라, 포맷 자체를 판별한다.
 */
class ReportPdfRendererFontTest {

    @Test
    @DisplayName("TrueType(.ttf) 폰트는 서브셋할 수 있다")
    void trueTypeFontIsSubsettable() {
        assertThat(ReportPdfRenderer.isSubsetUnsupported("NanumGothic.ttf")).isFalse();
    }

    @Test
    @DisplayName("CFF 기반 OTF/컬렉션 폰트는 서브셋할 수 없다")
    void openTypeAndCollectionFontsAreNotSubsettable() {
        // 실제로 운영에 깔려 있던 파일
        assertThat(ReportPdfRenderer.isSubsetUnsupported("NotoSansCJK-Regular.ttc")).isTrue();
        assertThat(ReportPdfRenderer.isSubsetUnsupported("NotoSansKR-Regular.otf")).isTrue();
        assertThat(ReportPdfRenderer.isSubsetUnsupported("SomeFont.otc")).isTrue();
    }

    @Test
    @DisplayName("확장자 대소문자를 가리지 않는다")
    void extensionCheckIsCaseInsensitive() {
        assertThat(ReportPdfRenderer.isSubsetUnsupported("NotoSansCJK-Regular.TTC")).isTrue();
        assertThat(ReportPdfRenderer.isSubsetUnsupported("NanumGothic.TTF")).isFalse();
    }

    /** 모르는 확장자까지 경고하면 매번 거짓 경보가 뜬다. 확실히 못 쓰는 포맷만 잡는다. */
    @Test
    @DisplayName("알 수 없는 확장자와 null 은 경고 대상이 아니다")
    void unknownExtensionIsNotFlagged() {
        assertThat(ReportPdfRenderer.isSubsetUnsupported("font.woff2")).isFalse();
        assertThat(ReportPdfRenderer.isSubsetUnsupported("fontWithoutExtension")).isFalse();
        assertThat(ReportPdfRenderer.isSubsetUnsupported(null)).isFalse();
    }
}
