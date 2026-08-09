package com.mcp.common.delivery;

/**
 * 投递状态 — 单条消息的投递生命周期状态。
 */
public enum DeliveryStatus {
    PENDING,
    SCHEDULED,
    SENDING,
    DELIVERED,
    FAILED,
    RETRYING,
    CANCELLED,
    PARTIAL
}