package com.ispengya.hotkey.example;

import com.ispengya.hotkey.cli.HotKeyClient;
import com.ispengya.hotkey.cli.spring.EnableHotKey;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HotKey 示例应用。
 *
 * <p>模拟一个需要热 key 探测的服务：
 * <ul>
 *     <li>{@code GET /demo?key=xxx} — 获取 key 对应的值，自动上报访问统计</li>
 *     <li>{@code GET /hot?key=xxx} — 查询指定 key 是否为热 key</li>
 *     <li>{@code GET /stats} — 查看本地统计信息</li>
 * </ul>
 */
@SpringBootApplication
@EnableHotKey
public class HotKeyExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotKeyExampleApplication.class, args);
    }

    @RestController
    static class DemoController {

        private final HotKeyClient hotKeyClient;

        /** 模拟数据库 */
        private final Map<String, String> mockDb = new ConcurrentHashMap<>();

        public DemoController(HotKeyClient hotKeyClient) {
            this.hotKeyClient = hotKeyClient;
            // 初始化一些测试数据
            mockDb.put("user_1", "Alice");
            mockDb.put("user_2", "Bob");
            mockDb.put("user_3", "Charlie");
            mockDb.put("sku_100", "iPhone 15");
            mockDb.put("sku_200", "MacBook Pro");
        }

        /**
         * 获取 key 对应的值。自动接入热 key 探测。
         *
         * <pre>
         * 1. hotKeyClient.get(key) 检查本地缓存
         * 2. 缓存未命中 → 回源查库，同时上报访问统计给 Worker
         * 3. 如果 key 是热 key → Worker 推送标记，后续请求直接本地缓存命中
         * </pre>
         */
        @GetMapping("/demo")
        public String demo(@RequestParam("key") String key) {
            // 先查 hotkey 本地缓存
            Object cached = hotKeyClient.get(key);
            if (cached != null) {
                return "HIT: " + cached;
            }

            // 回源查库
            String value = mockDb.get(key);
            if (value == null) {
                value = "default-" + key + "-" + System.currentTimeMillis();
                mockDb.put(key, value);
            }

            // 写入本地缓存（只有热 key 才会真正存入）
            hotKeyClient.set(key, value);

            return "MISS: " + value;
        }

        /**
         * 判断指定 key 是否为热 key。
         */
        @GetMapping("/hot")
        public Map<String, Object> isHot(@RequestParam("key") String key) {
            boolean hot = hotKeyClient.isHotKey(key);
            return Map.of("key", key, "hot", hot);
        }

        /**
         * 查看统计信息。
         */
        @GetMapping("/stats")
        public Map<String, Object> stats() {
            return Map.of(
                    "appName", "example-app",
                    "description", "Use /demo?key=xxx to test hot key detection"
            );
        }
    }
}
