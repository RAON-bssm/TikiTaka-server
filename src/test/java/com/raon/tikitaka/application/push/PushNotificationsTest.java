package com.raon.tikitaka.application.push;

import com.raon.tikitaka.global.config.PushProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationsTest {

    private final PushNotifications defaults = new PushNotifications(PushProperties.defaults());

    @Test
    void 일급_알림_기본_문구와_타입() {
        PushMessage m = defaults.dailyWage();

        assertThat(m.title()).isEqualTo("티키타카");
        assertThat(m.body()).isEqualTo("출석 보상이 기다리는 중입니다!!");
        assertThat(m.data()).containsEntry("type", "DAILY_WAGE");
    }

    @Test
    void 땅_뺏김_알림에_도전자_닉네임과_동_이름이_들어간다() {
        PushMessage m = defaults.territoryCaptured("티키", "온천동", 2626010100L);

        assertThat(m.body()).isEqualTo("티키에게 온천동 땅을 뺏겼습니다!!");
        assertThat(m.data())
                .containsEntry("type", "TERRITORY_CAPTURED")
                .containsEntry("dong_code", "2626010100");
    }

    @Test
    void 동_이름이_없으면_동_없는_문구를_쓴다() {
        PushMessage m = defaults.territoryCaptured("티키", null, null);

        assertThat(m.body()).isEqualTo("티키에게 땅을 뺏겼습니다!!");
        assertThat(m.data()).doesNotContainKey("dong_code");
    }

    @Test
    void 닉네임이_없으면_누군가로_보낸다() {
        PushMessage m = defaults.territoryCaptured(" ", "온천동", 1L);

        assertThat(m.body()).isEqualTo("누군가에게 온천동 땅을 뺏겼습니다!!");
    }

    @Test
    void 환경변수로_바꾼_문구를_쓴다() {
        PushNotifications custom = new PushNotifications(new PushProperties("땅따먹기", "일급 받아 가세요!",
                "[{dong}] {challenger}님이 땅을 가져갔어요", "{challenger}님이 땅을 가져갔어요"));

        assertThat(custom.dailyWage().title()).isEqualTo("땅따먹기");
        assertThat(custom.dailyWage().body()).isEqualTo("일급 받아 가세요!");
        assertThat(custom.territoryCaptured("티키", "온천동", 1L).body()).isEqualTo("[온천동] 티키님이 땅을 가져갔어요");
    }
}
