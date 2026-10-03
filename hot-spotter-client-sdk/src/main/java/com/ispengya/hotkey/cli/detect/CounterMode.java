package com.ispengya.hotkey.cli.detect;

/**
 * 访问计数器的实现策略。
 *
 * @author ispengya
 */
public enum CounterMode {

    /**
     * 双缓冲无锁：两个 CHM 交替读写，drain 不阻塞写入（默认）。
     */
    DOUBLE,

    /**
     * 单 map + 锁：drain 期间阻塞所有写入，用于性能对比。
     */
    LOCK
}
