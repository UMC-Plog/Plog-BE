package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 프로퍼티 바인딩과 조건부 빈 등록을 DB 없이 검증한다.
 * <p>
 * 이 프로젝트는 컨텍스트를 통째로 띄우는 테스트가 Docker 기반 E2E 뿐이라, 프로퍼티
 * 오타나 바인딩 실패가 <b>배포 후 기동 로그에서야</b> 드러난다. ApplicationContextRunner
 * 는 슬라이스만 띄우므로 그 공백을 메운다(S3DisabledContextTest 와 같은 방식).
 */
class ThumbnailContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ThumbnailPropertiesConfig.class, ThumbnailScheduler.class)
            .withBean(UploadedFileRepository.class, () -> mock(UploadedFileRepository.class))
            .withBean(FileStorageService.class, () -> mock(FileStorageService.class))
            .withBean(ThumbnailInvoker.class, () -> mock(ThumbnailInvoker.class));

    /**
     * 꺼진 환경에서 스케줄러가 뜨면 3초마다 빈 쿼리가 나간다. 그보다 나쁜 것은,
     * 나중에 켤 때 밀린 PENDING 이 한꺼번에 Lambda 로 터지는 것이다.
     */
    @Test
    void 비활성화면_스케줄러가_뜨지_않는다() {
        contextRunner.withPropertyValues(
                        "plog.thumbnail.enabled=false",
                        "plog.thumbnail.function-name=plog-thumbnail",
                        "plog.thumbnail.max-edge=640")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ThumbnailScheduler.class);
                    assertThat(context.getBean(ThumbnailProperties.class).enabled()).isFalse();
                });
    }

    @Test
    void 활성화하면_스케줄러가_뜨고_설정이_바인딩된다() {
        contextRunner.withPropertyValues(
                        "plog.thumbnail.enabled=true",
                        "plog.thumbnail.function-name=plog-thumbnail",
                        "plog.thumbnail.max-edge=640")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ThumbnailScheduler.class);

                    ThumbnailProperties properties = context.getBean(ThumbnailProperties.class);
                    assertThat(properties.functionName()).isEqualTo("plog-thumbnail");
                    assertThat(properties.maxEdge()).isEqualTo(640);
                });
    }

    /** 켜 놓고 함수 이름을 빠뜨리면 조용히 도는 것보다 기동 실패가 낫다. */
    @Test
    void 활성화했는데_함수_이름이_없으면_기동에_실패한다() {
        contextRunner.withPropertyValues(
                        "plog.thumbnail.enabled=true",
                        "plog.thumbnail.function-name=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void maxEdge를_주지_않으면_640으로_떨어진다() {
        contextRunner.withPropertyValues(
                        "plog.thumbnail.enabled=false",
                        "plog.thumbnail.function-name=plog-thumbnail")
                .run(context -> assertThat(context.getBean(ThumbnailProperties.class).maxEdge())
                        .isEqualTo(640));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ThumbnailProperties.class)
    static class ThumbnailPropertiesConfig {
    }
}
