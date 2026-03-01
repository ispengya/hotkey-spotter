package com.ispengya.hotkey.example;

import com.ispengya.hotkey.cli.core.HotKeyClient;
import com.ispengya.hotkey.cli.core.ValueLoader;
import com.ispengya.hotkey.cli.spring.EnableHotKey;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableHotKey
public class HotKeyExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotKeyExampleApplication.class, args);
    }

    @RestController
    static class DemoController {

        private final HotKeyClient hotKeyClient;

        public DemoController(HotKeyClient hotKeyClient) {
            this.hotKeyClient = hotKeyClient;
        }

        @GetMapping("/demo")
        public String demo(@RequestParam("key") String key) {
            return hotKeyClient.get(key, key1 -> {
                return "value-for-" + key1 + "-" + System.currentTimeMillis();
            });
        }
    }
}

