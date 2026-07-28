package com.neobank.module.model;

/**
 * 卡片生命周期状态 — 与 {@link Decision}（编排器回调用）完全不同。
 *
 * <p>{@link Decision} 用于报告编排器 ACCEPTED/REJECTED/REFERRED；
 * 此枚举描述本模块内部卡片记录的生成进度。</p>
 */
public enum Outcome {

    /** 卡片记录已创建，尚未发卡 */
    IN_PROGRESS,

    /** PAN 已生成，发卡指令已发送至制卡局 */
    ISSUED,

    /** 发卡流程失败（PAN 生成失败 / 制卡局拒绝 / 地址无效等） */
    FAILED
}
