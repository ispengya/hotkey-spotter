package com.ispengya.hotkey.common.exception;

/**
 * HotKeyCacheException 表示在缓存读取或回源过程中发生的异常，
 * 统一封装底层异常原因，方便业务侧统一处理。
 *
 * 例如本地缓存访问失败、回源逻辑抛出异常等场景。
 *
 * @author ispengya
 * @date 2026/1/17 11:42
 */
public class HotKeyCacheException extends RuntimeException {

    /**
     * 使用异常信息构造 HotKeyCacheException。
     *
     * @param message 异常描述信息
     */
    public HotKeyCacheException(String message) {
        super(message);
    }

    /**
     * 使用异常信息和底层原因构造 HotKeyCacheException。
     *
     * @param message 异常描述信息
     * @param cause   底层异常原因
     */
    public HotKeyCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}

