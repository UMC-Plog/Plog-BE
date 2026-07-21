package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InviteTokenGeneratorTest {

    @Test
    void generatesUniqueUrlSafeTokensWith256BitsOfRandomData() {
        InviteTokenGenerator generator = new InviteTokenGenerator();

        Set<String> tokens = new HashSet<>();
        for (int index = 0; index < 100; index++) {
            String token = generator.generate();

            assertThat(token).matches("^[A-Za-z0-9_-]+$");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            tokens.add(token);
        }

        assertThat(tokens).hasSize(100);
    }
}
