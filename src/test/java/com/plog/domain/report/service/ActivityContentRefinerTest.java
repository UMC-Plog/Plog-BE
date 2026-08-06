package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityContentRefinerTest {

    @Test
    void 공백을_정리한다() {
        RefinedContent refined = ActivityContentRefiner.refine("  안녕   하세요  \n\n 잘지내죠 ");

        assertThat(refined.noise()).isFalse();
        assertThat(refined.cleanContent()).isEqualTo("안녕 하세요 잘지내죠");
    }

    @Test
    void 반복된_웃음을_두_글자로_축약한다() {
        RefinedContent refined = ActivityContentRefiner.refine("ㅋㅋㅋㅋㅋㅋ 대박이네요 ㅎㅎㅎㅎㅎ");

        assertThat(refined.noise()).isFalse();
        assertThat(refined.cleanContent()).isEqualTo("ㅋㅋ 대박이네요 ㅎㅎ");
    }

    @Test
    void 이모지를_제거한다() {
        RefinedContent refined = ActivityContentRefiner.refine("좋아요 😀👍🎉 확인했습니다");

        assertThat(refined.noise()).isFalse();
        assertThat(refined.cleanContent()).isEqualTo("좋아요 확인했습니다");
    }

    @Test
    void ㅇㅇ_단독_입력은_동의로_치환한다() {
        RefinedContent refined = ActivityContentRefiner.refine("ㅇㅇ");

        assertThat(refined.noise()).isFalse();
        assertThat(refined.cleanContent()).isEqualTo("동의");
    }

    @Test
    void ㄱㄱ_단독_입력은_진행으로_치환한다() {
        RefinedContent refined = ActivityContentRefiner.refine("  ㄱㄱ  ");

        assertThat(refined.noise()).isFalse();
        assertThat(refined.cleanContent()).isEqualTo("진행");
    }

    @Test
    void 단독_감탄사는_노이즈로_판정한다() {
        RefinedContent refined = ActivityContentRefiner.refine("음");

        assertThat(refined.noise()).isTrue();
        assertThat(refined.cleanContent()).isNull();
    }

    @Test
    void 순수_웃음만_있는_메시지는_노이즈로_판정한다() {
        RefinedContent refined = ActivityContentRefiner.refine("ㅋㅋㅋㅋㅋㅋㅋㅋ");

        assertThat(refined.noise()).isTrue();
        assertThat(refined.cleanContent()).isNull();
    }

    @Test
    void 공백만_있는_내용은_노이즈로_판정한다() {
        RefinedContent refined = ActivityContentRefiner.refine("     ");

        assertThat(refined.noise()).isTrue();
        assertThat(refined.cleanContent()).isNull();
    }

    @Test
    void 원본이_null이면_노이즈가_아니라_정제_대상이_아닌_것으로_처리한다() {
        RefinedContent refined = ActivityContentRefiner.refine(null);

        assertThat(refined.noise()).isFalse();
        assertThat(refined.cleanContent()).isNull();
        assertThat(refined.hasCleanContent()).isFalse();
    }

    @Test
    void 업무_관련_문장은_그대로_보존한다() {
        String business = "JWT 인증 로직 구현 완료했습니다. Swagger 오류도 같이 수정했어요.";

        RefinedContent refined = ActivityContentRefiner.refine(business);

        assertThat(refined.noise()).isFalse();
        assertThat(refined.cleanContent()).isEqualTo(business);
    }
}