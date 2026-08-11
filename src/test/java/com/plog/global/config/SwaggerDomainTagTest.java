package com.plog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.evaluation.controller.EvaluationController;
import com.plog.domain.evaluation.controller.SelfFeedbackController;
import com.plog.domain.integration.controller.docs.IntegrationControllerDoc;
import com.plog.domain.notification.controller.FcmTokenController;
import com.plog.domain.notification.controller.NotificationController;
import com.plog.domain.notification.controller.NotificationSettingsController;
import com.plog.domain.project.controller.ProjectController;
import com.plog.domain.project.controller.ProjectLeaveController;
import com.plog.domain.project.controller.ProjectSettingsController;
import com.plog.domain.project.controller.docs.ProjectControllerDoc;
import com.plog.domain.project.controller.docs.ProjectInvitationControllerDoc;
import com.plog.domain.project.controller.docs.ProjectInviteControllerDoc;
import com.plog.domain.project.controller.docs.ProjectJoinControllerDoc;
import com.plog.domain.project.controller.docs.ProjectListControllerDoc;
import com.plog.domain.project.controller.docs.ProjectMemberControllerDoc;
import com.plog.domain.project.controller.docs.ProjectRoleControllerDoc;
import com.plog.domain.report.controller.docs.ReportControllerDoc;
import com.plog.domain.user.controller.AuthController;
import com.plog.domain.user.controller.EmailVerificationController;
import com.plog.domain.user.controller.SignupController;
import com.plog.domain.user.controller.docs.PasswordResetControllerDoc;
import com.plog.domain.user.controller.docs.ProfileControllerDoc;
import com.plog.domain.user.controller.docs.SocialAuthControllerDoc;
import com.plog.domain.user.controller.docs.UserControllerDoc;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

class SwaggerDomainTagTest {

    private static final String AUTH_DESCRIPTION =
            "회원가입, 로그인, 소셜 인증, 이메일 인증 및 비밀번호 재설정 API";
    private static final String USER_DESCRIPTION = "사용자 계정 및 프로필 관리 API";
    private static final String NOTIFICATION_DESCRIPTION = "알림 조회·설정 및 FCM 토큰 관리 API";
    private static final String PROJECT_DESCRIPTION = "프로젝트, 멤버 및 설정 관리 API";
    private static final String REPORT_DESCRIPTION = "피어 평가, 자기 피드백 및 리포트 생성·조회 API";
    private static final String INTEGRATION_DESCRIPTION = "외부 서비스 계정·리소스 연동 및 활동 로그 수집 API";

    @Test
    void groupsRelatedControllersUnderDomainTags() {
        assertTag("Auth", AUTH_DESCRIPTION,
                AuthController.class,
                SignupController.class,
                EmailVerificationController.class,
                SocialAuthControllerDoc.class,
                PasswordResetControllerDoc.class);
        assertTag("User", USER_DESCRIPTION,
                UserControllerDoc.class,
                ProfileControllerDoc.class);
        assertTag("Notification", NOTIFICATION_DESCRIPTION,
                NotificationController.class,
                NotificationSettingsController.class,
                FcmTokenController.class);
        assertTag("Project", PROJECT_DESCRIPTION,
                ProjectController.class,
                ProjectControllerDoc.class,
                ProjectInvitationControllerDoc.class,
                ProjectInviteControllerDoc.class,
                ProjectJoinControllerDoc.class,
                ProjectListControllerDoc.class,
                ProjectMemberControllerDoc.class,
                ProjectRoleControllerDoc.class,
                ProjectLeaveController.class,
                ProjectSettingsController.class);
        assertTag("Report", REPORT_DESCRIPTION,
                ReportControllerDoc.class,
                SelfFeedbackController.class,
                EvaluationController.class);
        assertTag("Integration", INTEGRATION_DESCRIPTION, IntegrationControllerDoc.class);
    }

    private void assertTag(String name, String description, Class<?>... types) {
        for (Class<?> type : types) {
            Tag tag = type.getAnnotation(Tag.class);
            assertThat(tag)
                    .as("%s Swagger tag", type.getSimpleName())
                    .isNotNull();
            assertThat(tag.name()).isEqualTo(name);
            assertThat(tag.description()).isEqualTo(description);
        }
    }
}
