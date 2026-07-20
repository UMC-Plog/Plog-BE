package com.plog.domain.project.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProjectTest {

    @Test
    void rejectsABlankInviteTokenHashDuringRotation() {
        Project project = Project.builder().build();

        assertThatThrownBy(() -> project.rotateInviteToken(" ", "encrypted-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invite token values must not be blank");
    }

    @Test
    void rejectsABlankEncryptedInviteTokenDuringRotation() {
        Project project = Project.builder().build();

        assertThatThrownBy(() -> project.rotateInviteToken("token-hash", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invite token values must not be blank");
    }
}
