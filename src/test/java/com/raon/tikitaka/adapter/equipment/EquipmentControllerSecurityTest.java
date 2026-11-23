package com.raon.tikitaka.adapter.equipment;

import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.application.equipment.in.EquipItemUseCase;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.global.exception.InvalidTokenException;
import com.raon.tikitaka.global.security.JwtAccessDeniedHandler;
import com.raon.tikitaka.global.security.JwtAuthenticationEntryPoint;
import com.raon.tikitaka.global.security.SecurityConfig;
import com.raon.tikitaka.global.security.SecurityErrorWriter;
import com.raon.tikitaka.global.security.jwt.JwtProvider;
import com.raon.tikitaka.global.security.jwt.TokenErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다른 유저 장비 조회(GET /api/equipment/{userId})는 비로그인도 호출할 수 있고,
 * 본인 장비 조회(GET /api/equipment)와 착용(PATCH /api/equipment)은 계속 로그인이 필요한지 실제 보안 설정으로 확인한다.
 */
@WebMvcTest(EquipmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        SecurityErrorWriter.class})
class EquipmentControllerSecurityTest {

    private static final UUID TARGET_USER_ID = UUID.fromString("11111111-1111-1111-1111-000000000002");

    @Autowired MockMvc mockMvc;

    @MockitoBean EquipItemUseCase equipItemUseCase;
    @MockitoBean GetEquippedItemsUseCase getEquippedItemsUseCase;
    @MockitoBean JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        when(getEquippedItemsUseCase.getEquippedItems(any())).thenReturn(EquippedItems.empty());
        when(jwtProvider.parseAccessToken("expired-access-token"))
                .thenThrow(new InvalidTokenException(TokenErrorCode.TOKEN_EXPIRED, "expired"));
    }

    @Test
    void 다른_유저_장비는_토큰_없이도_조회된다() throws Exception {
        mockMvc.perform(get("/api/equipment/" + TARGET_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
        verify(getEquippedItemsUseCase).getEquippedItems(TARGET_USER_ID);
    }

    @Test
    void 다른_유저_장비는_만료된_토큰이어도_비로그인으로_조회된다() throws Exception {
        mockMvc.perform(get("/api/equipment/" + TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-access-token"))
                .andExpect(status().isOk());
    }

    @Test
    void 본인_장비_조회는_토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/equipment"))
                .andExpect(status().isUnauthorized());
        verify(getEquippedItemsUseCase, never()).getEquippedItems(any());
    }

    @Test
    void 장비_착용은_토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(patch("/api/equipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verify(equipItemUseCase, never()).equip(any(), any(), any());
    }
}
