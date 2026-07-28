package com.neobank.module.dto;

import com.neobank.module.model.CardRecord;
import java.time.Instant;

/**
 * {@code GET /api/v1/applications} 返回的展示视图。
 *
 * <p>此模块自己 UI 读取的字段。与之前的 {@code DemoShowcaseView} 不同，
 * 这里没有 {@code status} 字段 — 返回字段是 {@code outcome}（卡片生命周期）。</p>
 */
public record CardRecordView(
        String applicationId,
        String outcome,
        String reference,
        String panLast4,
        String productCode,
        String accountId,
        Instant createdAt) {

    public static CardRecordView of(CardRecord row) {
        return new CardRecordView(
                row.getApplicationId(),
                row.getOutcome(),
                row.getReference(),
                row.getPanLast4(),
                row.getProductCode(),
                row.getAccountId(),
                row.getCreatedAt());
    }
}
