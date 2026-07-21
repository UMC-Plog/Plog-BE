package com.plog.domain.project.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProjectTest {

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
