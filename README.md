## hk-cache / hotkey

基于滑动时间窗口的分布式热点 Key 探测与下发组件，按应用维度汇总访问统计，自动判定热 Key，并通过长连接将热 Key 集推送给客户端，本地进行热点缓存保护。

---

## 模块说明

- hkcache-server  
  热点 Key 服务端，负责接收访问上报、按 appName 做滑动窗口聚合、判定热 Key、维护结果并推送给客户端。

- hkcache-remoting  
  通信层，基于 Netty 自定义协议，提供客户端和服务端通用的 Command 编解码和请求分发能力。

- hotkey-spring-boot-starter  
  Spring Boot 客户端集成模块，提供配置绑定、自动装配、本地缓存以及访问统计上报封装。

- hkcache-example  
  示例 Spring Boot 应用，演示如何在业务中集成 hk-cache。

---

## 服务端使用

### 启动

入口类在：

- hkcache-server/src/main/java/com/ispengya/hkcache/server/HotKeyServerApplication.java

直接以普通 Java 应用方式启动即可：

```bash
cd hkcache-server
mvn package
java -jar target/hkcache-server-*.jar
```

### 关键配置

配置文件：

- hkcache-server/src/main/resources/hotkey-server.properties

默认配置（精简版）：

```properties
server.port=8888

aggregator.windowSizeMillis=1000
aggregator.windowSlotCount=30

algorithm.minCountThreshold=3

scheduler.corePoolSize=1
scheduler.workerPoolSize=4
scheduler.periodMillis=1000
scheduler.decayPeriodMillis=60000
scheduler.hotKeyIdleMillis=60000
```

含义：

- 滑动窗口长度约为 1000ms × 30 ≈ 30 秒
- 某个 appName + key 在最近 30 秒内访问次数 ≥ 3 即被判为热 Key
- 热 Key 若连续空闲时间超过 60000ms 会被冷却并从结果集中移除

---

## 客户端使用（Spring Boot）

### 引入依赖

在业务工程的 pom.xml 中引入：

```xml
<dependency>
    <groupId>com.ispengya</groupId>
    <artifactId>hotkey-spring-boot-starter</artifactId>
    <version>你的版本号</version>
</dependency>
```

### 配置示例

配置前缀为 `hkcache`，示例：

```yaml
hkcache:
  enabled: true
  app-name: hkcache-example

  client:
    server-addresses:
      - "127.0.0.1:8888"

    # 以下为可选项，不配置则使用默认值
    # connect-timeout-millis: 3000
    # worker-threads: 4
    # max-frame-bytes: 1048576
    # push-pool-size: 1
    # report-pool-size: 2

    # 本地缓存配置
    # local-cache-maximum-size: 1000
    # local-cache-expire-after-write-millis: 300000

    # 自定义实现类（一般情况下不需要配置）
    # cache-template-class: "com.example.CustomCacheTemplate"
    # local-cache-class: "com.example.CustomLocalCache"
    # safe-load-executor-class: "com.example.CustomSafeLoadExecutor"
```

### 开启 hkcache 客户端

在应用入口类上添加 `@EnableHotKey` 注解：

```java
import com.ispengya.hotkey.cli.spring.EnableHotKey;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableHotKey
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### HotKeyClient 使用示例

业务代码通过注入 `HotKeyClient` 作为统一访问入口：

```java
import com.ispengya.hotkey.cli.core.HotKeyClient;
import org.springframework.stereotype.Service;

@Service
public class DemoService {

    private final HotKeyClient hotKeyClient;

    public DemoService(HotKeyClient hotKeyClient) {
        this.hotKeyClient = hotKeyClient;
    }

    public String getValue(String key) {
        return hotKeyClient.get(key, this::loadFromDb);
    }

    private String loadFromDb(String key) {
        return "value-of-" + key;
    }
}
```

## 核心流程

1. 客户端本地访问与统计
   - 业务通过 HotKeyClient.get(...) 作为统一入口访问缓存/回源
   - HotKeyClient 内部使用 HotKeySet 判断是否热 key、HotKeyDetector 记录访问，并委托 CacheTemplate 完成加载

2. 上报访问到服务端
   - HotKeyRemotingClient 使用 Netty 与 hkcache-server 建立长连接
   - 定时将 AccessReportMessage 发送到服务端，对应服务端的 AccessReportPipeline

3. 按 appName 维度写入滑动窗口
   - AccessReportPipeline 从队列中消费 AccessReport
   - 通过 InstanceWindowRegistry.selectWindowForApp(appName) 选择该应用的 SlidingWindowInstanceAggStore
   - 调用 addReport(report) 把当前访问写入对应时间片 WindowSlot

4. 滑动窗口聚合与热 Key 判定
   - 对单个 key，HotKeyComputeTask.computeAndPublish 会：
     - 调用 snapshotForKey(key) 在当前滑动窗口内合并各时间片的统计，得到 AggregatedKeyStat
     - 使用 HotKeyComputeAlgorithm 按阈值判断是否为热 Key

5. 结果存储与推送
   - 若判定为热 Key，则更新 HotKeyResultStore 中该 appName 的热 Key 集合
   - HotKeyChangePublisher 将新增/移除的热 Key 通过推送通道（按 appName 注册的 channel）通知到所有客户端

6. 客户端接收并应用热 Key
   - 客户端通过 registerPushChannel 注册推送通道
   - 收到 HOT_KEY_PUSH 后，更新本地的 HotKey 集合和缓存策略，后续访问可以直接命中本地并进行保护

7. 热 Key 衰减
   - HotKeyScheduler 定期调度 HotKeyDecayTask
   - HotKeyDecayTask 根据最后活跃时间和 hotKeyIdleMillis 清理长时间未活跃的热 Key，并下发冷却通知
