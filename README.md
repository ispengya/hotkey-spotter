## hot-spotter

基于滑动时间窗口的分布式热点 Key 探测与下发组件，按应用维度汇总访问统计，自动判定热 Key，并通过长连接将热 Key 集推送给客户端，本地进行热点缓存保护。

---

## 模块说明

- hot-spotter-server  
  服务端，接收访问上报，按 appName 做滑动窗口聚合，判定热 Key，维护结果并推送给客户端。

- hot-spotter-remoting  
  通信层，基于 Netty 自定义协议，提供客户端与服务端通用的 Command 编解码与请求分发。

- hot-spotter-client-sdk  
  客户端 SDK（Spring Boot Starter），提供配置绑定、自动装配、访问统计上报、推送接收与本地缓存保护。

- hot-spotter-common  
  公共模块，提供通用模型与基础工具。

---

## 服务端使用

### 启动

入口类：

- hot-spotter-server/src/main/java/com/ispengya/hotkey/server/HotKeyServerApplication.java

启动方式：

```bash
cd hot-spotter-server
mvn package
java -jar target/hot-spotter-server-*.jar
```

### 关键配置

配置文件：

- hot-spotter-server/src/main/resources/hotkey-server.properties

默认配置（摘录）：

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

说明：

- 滑动时间窗口 ≈ 1000ms × 30 ≈ 30 秒
- 在最近窗口内（30 秒）访问次数 ≥ 3 的 key 被判定为热 Key
- 热 Key 若连续空闲超过 60000ms 将被移除
- 服务端对上报数据按“服务端接收时间”落桶，减小上报延迟对统计的影响

---

## 客户端使用（Spring Boot）

### 引入依赖

在业务工程的 pom.xml 中引入：

```xml
<dependency>
    <groupId>com.ispengya</groupId>
    <artifactId>hot-spotter-client-sdk</artifactId>
    <version>你的版本号</version>
    <!-- 若使用 Spring Boot 自动装配，请在父工程或依赖中包含 spring-boot-autoconfigure -->
</dependency>
```

### 配置示例

配置前缀为 `hotkey`，示例：

```yaml
hotkey:
  enabled: true
  app-name: hotkey-example
  client:
    server-addresses:
      - "127.0.0.1:8888"
    # 可选项
    # connect-timeout-millis: 3000
    # worker-threads: 4
    # max-frame-bytes: 1048576
    # push-pool-size: 1
    # report-pool-size: 2
    # 本地缓存
    # local-cache-maximum-size: 1000
    # local-cache-expire-after-write-millis: 300000
```

### 开启客户端

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

---

## 核心流程

1. 客户端本地访问与统计  
   HotKeyClient 作为统一入口；HotKeySet 判断是否热 Key，HotKeyDetector 记录访问并委托 CacheTemplate 加载。

2. 上报访问到服务端  
   通过 hot-spotter-remoting 与服务端保持长连，定期批量上报 AccessReport。

3. 写入滑动窗口  
   AccessReportPipeline 消费上报；InstanceWindowRegistry 选择对应应用的 SlidingWindowInstanceAggStore；按服务端接收时间落桶。

4. 聚合与判定  
   聚合快照在最近窗口内合并各时间片；HotKeyComputeAlgorithm 根据阈值判定热 Key。

5. 结果存储与推送  
   更新 HotKeyResultStore 后，HotKeyChangePublisher 将新增/移除的热 Key 推送给客户端。

6. 客户端接收并应用  
   客户端更新本地热 Key 集合，配合本地缓存策略保护热点。

7. 热 Key 衰减  
   HotKeyDecayTask 按 idleMillis 清理长时间未活跃的热 Key，并下发冷却通知。
