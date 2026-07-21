package com.plog.domain.project.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProjectTest {

    private static final LocalDate END_DAY = LocalDate.of(2026, 7, 21);

    private static Project projectEndingOnEndDay(ProjectStatus status) {
        return Project.builder()
                .inviteTokenHash("token-hash")
                .inviteTokenEncrypted("encrypted-token")
                .status(status)
                .startDay(END_DAY.minusDays(30))
                .endDay(END_DAY)
                .build();
    }

    @Test
    void staysEvaluatingOnTheEndDayItself() {
        Project project = projectEndingOnEndDay(ProjectStatus.IN_PROGRESS);

        assertThat(project.isEvaluatingState(END_DAY)).isTrue();
    }

    @Test
    void stopsEvaluatingTheDayAfterTheEndDay() {
        Project project = projectEndingOnEndDay(ProjectStatus.IN_PROGRESS);

        assertThat(project.isEvaluatingState(END_DAY.plusDays(1))).isFalse();
    }

    @Test
    void isNeverEvaluatingOnceCompleted() {
        Project project = projectEndingOnEndDay(ProjectStatus.COMPLETED);

        assertThat(project.isEvaluatingState(END_DAY)).isFalse();
    }

    @Test
    void rejectsABlankInviteTokenHashDuringCreation() {
        assertThatThrownBy(() -> Project.builder()
                .inviteTokenHash(" ")
                .inviteTokenEncrypted("encrypted-token")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invite token values must not be blank");
    }

    @Test
    void rejectsANullEncryptedInviteTokenDuringCreation() {
        assertThatThrownBy(() -> Project.builder()
                .inviteTokenHash("token-hash")
                .inviteTokenEncrypted(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invite token values must not be blank");
    }

    @Test
    void rejectsABlankEncryptedInviteTokenDuringCreation() {
        assertThatThrownBy(() -> Project.builder()
                .inviteTokenHash("token-hash")
                .inviteTokenEncrypted(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invite token values must not be blank");
    }

    @Test
    void rejectsABlankInviteTokenHashDuringRotation() {
        Project project = Project.builder()
                .inviteTokenHash("previous-hash")
                .inviteTokenEncrypted("previous-encrypted-token")
                .build();

        assertThatThrownBy(() -> project.rotateInviteToken(" ", "encrypted-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invite token values must not be blank");
    }

    @Test
    void rejectsANullEncryptedInviteTokenDuringRotation() {
        Project project = Project.builder()
                .inviteTokenHash("previous-hash")
                .inviteTokenEncrypted("previous-encrypted-token")
                .build();

        assertThatThrownBy(() -> project.rotateInviteToken("token-hash", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invite token values must not be blank");
    }
}
