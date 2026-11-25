package com.raon.tikitaka.adapter.product.dto;

import com.raon.tikitaka.application.gashapon.GashaponProduct;

import java.util.List;

public record GashaponListResponse(List<GashaponItemResponse> gashapon) {

    public static GashaponListResponse from(List<GashaponProduct> gashapons) {
        return new GashaponListResponse(gashapons.stream().map(GashaponItemResponse::from).toList());
    }
}
