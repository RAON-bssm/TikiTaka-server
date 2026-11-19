package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 회원가입 설정.
 *
 * @param bonusPoint 가입 시 지급하는 포인트. 따로 알림은 보내지 않는다
 */
@ConfigurationProperties(prefix = "signup")
public record SignupProperties(@DefaultValue("5000") int bonusPoint) {
}
