package com.plog.domain.integration.service;

import com.plog.global.util.HashUtil;
import java.util.Locale;

/** provider actor 식별값의 저장·선택 키 형식을 한 곳에서 관리한다. */
record ProviderActorKey(Type type, String value) {

    private static final String SELECTION_PREFIX = "actor:";

    enum Type {
        PROVIDER_ID("id:"),
        EMAIL("email:"),
        LOGIN("login:");

        private final String storagePrefix;

        Type(String storagePrefix) {
            this.storagePrefix = storagePrefix;
        }
    }

    static ProviderActorKey primary(String providerActorId, String providerLogin, String providerEmail) {
        if (hasText(providerActorId)) {
            return providerId(providerActorId);
        }
        if (hasText(providerEmail)) {
            return email(providerEmail);
        }
        return hasText(providerLogin) ? login(providerLogin) : null;
    }

    static ProviderActorKey providerId(String value) {
        return hasText(value) ? new ProviderActorKey(Type.PROVIDER_ID, value) : null;
    }

    static ProviderActorKey email(String value) {
        return hasText(value) ? new ProviderActorKey(Type.EMAIL, value.toLowerCase(Locale.ROOT)) : null;
    }

    static ProviderActorKey login(String value) {
        return hasText(value) ? new ProviderActorKey(Type.LOGIN, value.toLowerCase(Locale.ROOT)) : null;
    }

    static ProviderActorKey fromStored(String storedValue) {
        if (!hasText(storedValue)) {
            return null;
        }
        if (storedValue.startsWith(Type.EMAIL.storagePrefix)) {
            return email(storedValue.substring(Type.EMAIL.storagePrefix.length()));
        }
        if (storedValue.startsWith(Type.LOGIN.storagePrefix)) {
            return login(storedValue.substring(Type.LOGIN.storagePrefix.length()));
        }
        return providerId(storedValue);
    }

    String storageValue() {
        return type == Type.PROVIDER_ID ? value : type.storagePrefix + value;
    }

    String selectionKey() {
        return SELECTION_PREFIX + HashUtil.sha256Hex(type.storagePrefix + value);
    }

    String rawProviderId() {
        return type == Type.PROVIDER_ID ? value : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
