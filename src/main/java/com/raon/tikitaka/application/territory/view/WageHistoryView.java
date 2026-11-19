package com.raon.tikitaka.application.territory.view;

import java.time.LocalDate;
import java.util.List;

/**
 * 내 일급 지급 이력(최신순).
 *
 * @param totalAmount 아래 목록 금액의 합계
 */
public record WageHistoryView(long totalAmount, List<Item> wages) {

    /**
     * @param landCount      지급 시점에 가진 땅 개수
     * @param totalLandPrice 지급 시점 땅값 합계
     * @param rate           지급 비율(0.5 = 50%)
     * @param amount         받은 포인트 = floor(totalLandPrice × rate)
     */
    public record Item(Long wageId, Long cycleId, LocalDate paidOn, int landCount, long totalLandPrice,
                       double rate, int amount) {
    }
}
