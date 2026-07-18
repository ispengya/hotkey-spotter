package com.ispengya.hotkey.cache;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置。
 *
 * <p>用于配置三级缓存的参数。</p>
 *
 * @author ispengya
 */
public class CacheConfig {

    /**
     * L1 本地缓存配置
     */
    private boolean l1Enabled = true;
    private long l1MaxSize = 10000;
    private long l1Duration = 5;
    private TimeUnit l1TimeUnit = TimeUnit.MINUTES;

    /**
     * L2 Redis 缓存配置（可选）
     */
    private boolean l2Enabled = false;
    private long l2Duration = 30;
    private TimeUnit l2TimeUnit = TimeUnit.MINUTES;

    /**
     * L3 数据源缓存配置
     */
    private boolean l3Enabled = true;
    private long l3Duration = 60;
    private TimeUnit l3TimeUnit = TimeUnit.MINUTES;

    public CacheConfig() {
    }

    // L1 配置
    public boolean isL1Enabled() {
        return l1Enabled;
    }

    public void setL1Enabled(boolean l1Enabled) {
        this.l1Enabled = l1Enabled;
    }

    public long getL1MaxSize() {
        return l1MaxSize;
    }

    public void setL1MaxSize(long l1MaxSize) {
        this.l1MaxSize = l1MaxSize;
    }

    public long getL1Duration() {
        return l1Duration;
    }

    public void setL1Duration(long l1Duration) {
        this.l1Duration = l1Duration;
    }

    public TimeUnit getL1TimeUnit() {
        return l1TimeUnit;
    }

    public void setL1TimeUnit(TimeUnit l1TimeUnit) {
        this.l1TimeUnit = l1TimeUnit;
    }

    // L2 配置
    public boolean isL2Enabled() {
        return l2Enabled;
    }

    public void setL2Enabled(boolean l2Enabled) {
        this.l2Enabled = l2Enabled;
    }

    public long getL2Duration() {
        return l2Duration;
    }

    public void setL2Duration(long l2Duration) {
        this.l2Duration = l2Duration;
    }

    public TimeUnit getL2TimeUnit() {
        return l2TimeUnit;
    }

    public void setL2TimeUnit(TimeUnit l2TimeUnit) {
        this.l2TimeUnit = l2TimeUnit;
    }

    // L3 配置
    public boolean isL3Enabled() {
        return l3Enabled;
    }

    public void setL3Enabled(boolean l3Enabled) {
        this.l3Enabled = l3Enabled;
    }

    public long getL3Duration() {
        return l3Duration;
    }

    public void setL3Duration(long l3Duration) {
        this.l3Duration = l3Duration;
    }

    public TimeUnit getL3TimeUnit() {
        return l3TimeUnit;
    }

    public void setL3TimeUnit(TimeUnit l3TimeUnit) {
        this.l3TimeUnit = l3TimeUnit;
    }

    @Override
    public String toString() {
        return "CacheConfig{" +
                "l1Enabled=" + l1Enabled +
                ", l1MaxSize=" + l1MaxSize +
                ", l1Duration=" + l1Duration + " " + l1TimeUnit +
                ", l2Enabled=" + l2Enabled +
                ", l2Duration=" + l2Duration + " " + l2TimeUnit +
                ", l3Enabled=" + l3Enabled +
                ", l3Duration=" + l3Duration + " " + l3TimeUnit +
                '}';
    }
}
