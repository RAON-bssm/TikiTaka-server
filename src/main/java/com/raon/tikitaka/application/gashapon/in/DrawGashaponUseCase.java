package com.raon.tikitaka.application.gashapon.in;

import com.raon.tikitaka.application.gashapon.GashaponDrawResult;

import java.util.List;
import java.util.UUID;

public interface DrawGashaponUseCase {

    List<GashaponDrawResult> draw(UUID userId, String gashaponProductId);
}
