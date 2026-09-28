package com.raon.tikitaka.adapter.product.dto;

import com.raon.tikitaka.application.gashapon.GashaponDrawResult;

import java.util.List;

public record GashaponDrawResponse(List<GashaponDrawItemResponse> results) {

    public static GashaponDrawResponse from(List<GashaponDrawResult> results) {
        return new GashaponDrawResponse(results.stream().map(GashaponDrawItemResponse::from).toList());
    }
}
