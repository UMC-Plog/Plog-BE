package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class IntegrationCredentialCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecryptsCredentialWithoutKeepingPlaintext() {
        IntegrationCredentialCipher cipher = new IntegrationCredentialCipher(KEY);

        String encrypted = cipher.encrypt("oauth-secret-token");

        assertThat(encrypted).isNotEqualTo("oauth-secret-token");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("oauth-secret-token");
    }

    @Test
    void usesDifferentCiphertextForTheSameCredential() {
        IntegrationCredentialCipher cipher = new IntegrationCredentialCipher(KEY);

        assertThat(cipher.encrypt("oauth-secret-token"))
                .isNotEqualTo(cipher.encrypt("oauth-secret-token"));
    }

    @Test
    void rejectsEncryptionWhenTheKeyIsNotConfigured() {
        IntegrationCredentialCipher cipher = new IntegrationCredentialCipher("");

        assertThatIllegalStateException().isThrownBy(() -> cipher.encrypt("oauth-secret-token"));
    }

    @Test
    void rejectsInvalidKeyLength() {
        String invalidKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IntegrationCredentialCipher(invalidKey));
    }
}
