package com.ispengya.hotkey.server.monitor;

import java.util.concurrent.atomic.LongAdder;

/**
 * Worker 端监控指标。
 *
 * <p>参考京东 hotkey 的 {@code InitConstant} 设计，
 * 在关键路径上埋点 LongAdder，无锁累加。</p>
 *
 * @author ispengya
 */
public final class MonitorMetrics {

    // ==================== 流水线计数器 ====================

    /** Netty 收到上报的 key 数量（ReportRequestHandler） */
    public static final LongAdder RECEIVED = new LongAdder();

    /** 成功入队 Pipeline 的 key 数量（AccessReportPipeline.submit） */
    public static final LongAdder OFFERED = new LongAdder();

    /** Pipeline 消费完成的 key 数量（consumeLoop 中 addCount 后） */
    public static final LongAdder DEALT = new LongAdder();

    /** 判定为热 Key 的次数 */
    public static final LongAdder HOT_KEY_DETECTED = new LongAdder();

    // ==================== 推送计数器 ====================

    /** 成功推送的次数 */
    public static final LongAdder PUSHED = new LongAdder();

    /** 推送失败的次数 */
    public static final LongAdder PUSH_FAILED = new LongAdder();

    // ==================== 延迟 / 耗时 ====================

    /**
     * Pipeline 排队 + 处理总延迟（毫秒）。
     * 从 Netty 收到上报到消费者线程消费完成的时间。
     * 平均延迟 = PIPELINE_LATENCY_TOTAL_MS.sum() / PIPELINE_LATENCY_COUNT.sum()
     */
    public static final LongAdder PIPELINE_LATENCY_TOTAL_MS = new LongAdder();

    /** Pipeline 延迟采样次数，用于计算平均值 */
    public static final LongAdder PIPELINE_LATENCY_COUNT = new LongAdder();

    /**
     * SlidingWindow.addCount() 总耗时（纳秒）。
     * 这个方法是 synchronized 的，是整个系统的硬瓶颈。
     * 平均耗时 = ADD_COUNT_TOTAL_NS.sum() / ADD_COUNT_COUNT.sum()
     */
    public static final LongAdder ADD_COUNT_TOTAL_NS = new LongAdder();

    /** addCount 采样次数，用于计算平均值 */
    public static final LongAdder ADD_COUNT_COUNT = new LongAdder();

    private MonitorMetrics() {
    }
}
