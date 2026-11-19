package com.raon.tikitaka.adapter.appversion.out;

import com.raon.tikitaka.domain.appversion.AppVersionSetting;
import com.raon.tikitaka.domain.enums.AppPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionSettingJpaRepository extends JpaRepository<AppVersionSetting, AppPlatform> {
}
