package com.plog.infrastructure.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plog.fcm", name = "enabled", havingValue = "true")
public class FirebaseFcmGateway implements FcmGateway {
    private final FirebaseMessaging firebaseMessaging;

    @Override
    @SuppressWarnings("deprecation")
    public void send(FcmMessage message) {
        Message firebaseMessage = Message.builder()
                .setToken(message.token())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();
        try {
            firebaseMessaging.send(firebaseMessage);
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode code = exception.getMessagingErrorCode();
            boolean invalidToken = code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.INVALID_ARGUMENT;
            throw new FcmDeliveryException(invalidToken, exception);
        }
    }
}
