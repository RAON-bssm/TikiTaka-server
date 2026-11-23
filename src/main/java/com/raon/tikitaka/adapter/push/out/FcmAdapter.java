package com.raon.tikitaka.adapter.push.out;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.raon.tikitaka.adapter.push.out.FcmErrorClassifier.Action;
import com.raon.tikitaka.adapter.push.out.FcmErrorClassifier.Response;
import com.raon.tikitaka.application.push.PushMessage;
import com.raon.tikitaka.application.push.PushSendResult;
import com.raon.tikitaka.application.push.out.PushSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase Admin SDK(HTTP v1)로 FCM 푸시를 보낸다.
 *
 * 키: Firebase 서비스 계정 JSON을 base64로 인코딩해 FIREBASE_CREDENTIALS_BASE64로 넣는다.
 * 비어 있으면 서버는 정상 기동하고 발송만 건너뛴다(메일 설정과 같은 방식). 값이 있는데 읽을 수 없으면 기동을 실패시킨다.
 *
 * 한 번에 최대 500개 토큰(FCM 한도)씩 sendEachForMulticast로 보내고, 토큰별 응답을 FcmErrorClassifier로 분류한다.
 * 5xx 재시도는 SDK가 기본으로 한다. 여기서는 결과를 로그로 남기고 죽은 토큰 목록만 돌려준다.
 */
@Slf4j
@Component
public class FcmAdapter implements PushSenderPort {

    static final int MULTICAST_LIMIT = 500;
    private static final String FIREBASE_APP_NAME = "tikitaka";

    /** null이면 발송 꺼짐 */
    private final FirebaseMessaging firebaseMessaging;

    @Autowired
    public FcmAdapter(@Value("${firebase.credentials-base64:}") String credentialsBase64) {
        this(initialize(credentialsBase64));
    }

    FcmAdapter(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    private static FirebaseMessaging initialize(String credentialsBase64) {
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS_BASE64가 없어 푸시 발송을 건너뜁니다.");
            return null;
        }
        try {
            byte[] json = Base64.getDecoder().decode(credentialsBase64.trim());
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json)))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(a -> a.getName().equals(FIREBASE_APP_NAME))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, FIREBASE_APP_NAME));
            log.info("Firebase 초기화 완료. projectId={}", app.getOptions().getProjectId());
            return FirebaseMessaging.getInstance(app);
        } catch (IOException | RuntimeException e) {
            // base64 형식 오류(IllegalArgumentException), JSON·키 형식 오류(IOException 또는 런타임 예외)
            throw new IllegalStateException("Firebase 서비스 계정 키를 읽을 수 없습니다. FIREBASE_CREDENTIALS_BASE64를 확인하세요.", e);
        }
    }

    @Override
    public PushSendResult send(List<String> tokens, PushMessage message) {
        if (firebaseMessaging == null) {
            log.debug("Firebase 설정 없음, 푸시 {}건 발송 생략", tokens.size());
            return PushSendResult.empty();
        }
        if (tokens.isEmpty()) {
            return PushSendResult.empty();
        }

        int success = 0;
        List<String> invalid = new ArrayList<>();
        for (int from = 0; from < tokens.size(); from += MULTICAST_LIMIT) {
            List<String> chunk = tokens.subList(from, Math.min(from + MULTICAST_LIMIT, tokens.size()));
            try {
                BatchResponse batch = firebaseMessaging.sendEachForMulticast(toMulticast(chunk, message));
                success += batch.getSuccessCount();
                invalid.addAll(handleResponses(chunk, batch.getResponses()));
            } catch (FirebaseMessagingException e) {
                // 요청 자체가 실패(인증, 네트워크 등). 토큰 문제가 아니므로 아무것도 지우지 않는다
                log.error("FCM 발송 요청 실패. code={}, 토큰 {}개", e.getMessagingErrorCode(), chunk.size(), e);
            }
        }
        return new PushSendResult(success, invalid);
    }

    /** 토큰별 응답을 분류해 로그를 남기고 지울 토큰을 돌려준다 */
    List<String> handleResponses(List<String> tokens, List<SendResponse> responses) {
        List<Action> actions = FcmErrorClassifier.classify(responses.stream().map(FcmAdapter::toResponse).toList());

        List<String> invalid = new ArrayList<>();
        Map<Action, Integer> counts = new EnumMap<>(Action.class);
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            counts.merge(action, 1, Integer::sum);
            if (action == Action.DELETE_TOKEN) {
                invalid.add(tokens.get(i));
            } else if (action == Action.PAYLOAD_ERROR || action == Action.CONFIG_ERROR) {
                // 메시지·설정 문제는 묶음 전체에 같은 원인이라 첫 건만 자세히 남긴다
                if (counts.get(action) == 1) {
                    log.error("FCM {}: {}", action, responses.get(i).getException().getMessage());
                }
            }
        }
        if (counts.getOrDefault(Action.SENT, 0) != actions.size()) {
            log.warn("FCM 발송 결과 {}", counts);
        }
        return invalid;
    }

    private static Response toResponse(SendResponse r) {
        if (r.isSuccessful()) {
            return Response.ok();
        }
        FirebaseMessagingException e = r.getException();
        return e == null
                ? Response.failed(null, null)
                : Response.failed(e.getMessagingErrorCode(), e.getMessage());
    }

    private static MulticastMessage toMulticast(List<String> tokens, PushMessage m) {
        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(m.title())
                        .setBody(m.body())
                        .build())
                .putAllData(m.data())
                // Android: 앱이 꺼져 있어도 바로 깨워 표시하게 높은 우선순위
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                // iOS: 기본 알림음
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound("default").build())
                        .build())
                .build();
    }
}
