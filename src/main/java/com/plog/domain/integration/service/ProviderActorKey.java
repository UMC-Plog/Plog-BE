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
        for (Type type : Type.values()) {
            if (storedValue.startsWith(type.storagePrefix)) {
                String rawValue = storedValue.substring(type.storagePrefix.length());
                return switch (type) {
                    case PROVIDER_ID -> providerId(rawValue);
                    case EMAIL -> email(rawValue);
                    case LOGIN -> login(rawValue);
                };
            }
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
