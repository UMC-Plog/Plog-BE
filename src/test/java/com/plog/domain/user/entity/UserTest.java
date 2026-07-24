package com.plog.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void createLocalStoresChosenProfilePreset() {
        User user = User.createLocal("user@plog.com", "encoded", "홍길동", "gildong", ProfilePreset.OTTER);

        assertThat(user.getProfilePreset()).isEqualTo(ProfilePreset.OTTER);
    }

    @Test
    void createLocalLeavesProfilePresetNullWhenNotChosen() {
        User user = User.createLocal("user@plog.com", "encoded", "홍길동", "gildong", null);

        assertThat(user.getProfilePreset()).isNull();
    }

    @Test
    void changeProfilePresetReplacesTheCurrentPreset() {
        User user = User.createLocal("user@plog.com", "encoded", "홍길동", "gildong", null);

        user.changeProfilePreset(ProfilePreset.PANDA);

        assertThat(user.getProfilePreset()).isEqualTo(ProfilePreset.PANDA);
    }
}
