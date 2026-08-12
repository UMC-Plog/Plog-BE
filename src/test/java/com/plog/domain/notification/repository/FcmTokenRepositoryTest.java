package com.plog.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class FcmTokenRepositoryTest {

    @Test
    void deletesInvalidTokenInNewTransaction() throws NoSuchMethodException {
        Transactional transactional = FcmTokenRepository.class
                .getMethod("deleteByToken", String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
