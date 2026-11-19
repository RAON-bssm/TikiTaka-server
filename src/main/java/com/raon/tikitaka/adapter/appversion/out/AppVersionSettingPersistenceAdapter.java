package com.raon.tikitaka.adapter.appversion.out;

import com.raon.tikitaka.application.appversion.out.AppVersionSettingPort;
import com.raon.tikitaka.domain.appversion.AppVersionSetting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * app_version 테이블 조회. 읽기만 한다. 트랜잭션은 다른 PersistenceAdapter처럼 따로 걸지 않고
 * 리포지토리 기본(읽기 전용) 트랜잭션을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class AppVersionSettingPersistenceAdapter implements AppVersionSettingPort {

    private final AppVersionSettingJpaRepository appVersionSettingJpaRepository;

    @Override
    public List<AppVersionSetting> findAll() {
        return appVersionSettingJpaRepository.findAll();
    }
}
