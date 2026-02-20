package com.ispengya.hotkey.cli.spring;

import com.ispengya.hotkey.cli.core.CacheTemplate;
import com.ispengya.hotkey.cli.core.HotKeyClient;
import com.ispengya.hotkey.cli.core.PostLoadAction;
import com.ispengya.hotkey.cli.detect.HotKeyDetector;
import com.ispengya.hotkey.cli.detect.HotKeySet;

/**
 * HotKeyClientFactory 封装 HotKeyClient 的创建逻辑，
 * 便于在 Spring 自动装配或多实例场景下复用构建过程。
 *
 * 当前实现仅支持创建单实例的 HotKeyClient。
 *
 * @author ispengya
 */
public class HotKeyClientFactory {

    public HotKeyClient createHotKeyClient(HotKeyDetector detector,
                                           HotKeySet hotKeySet,
                                           CacheTemplate cacheTemplate,
                                           PostLoadAction postLoadAction) {
        return new HotKeyClient(detector, hotKeySet, cacheTemplate, postLoadAction);
    }
}
