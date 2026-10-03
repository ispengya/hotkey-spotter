# hotkey-spotter

[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
[![Java](https://img.shields.io/badge/java-8-orange)]()
[![Spring Boot](https://img.shields.io/badge/spring--boot-2.7.18-brightgreen)]()
[![Netty](https://img.shields.io/badge/protocol-Netty%20custom-yellow)]()

基于滑动时间窗口的分布式热点 Key 探测与下发组件:按应用维度汇总访问统计,自动判定热 Key,通过长连接将热 Key 集合实时推送给客户端,在本地进行热点缓存保护。

> 曾用名 `hot-spotter`,模块目录与 Maven 坐标(`com.ispengya:hot-spotter-*`)暂未改名。

## ✨ 特性一览

- **滑动窗口判定**:服务端按 appName 聚合上报数据,窗口内访问频次达到阈值即判定为热 Key,空闲超时自动衰减移除
- **长连接推送**:Netty 自定义协议,热 Key 新增/移除秒级下发到所有在线客户端
- **两种计数采集模式**:客户端 SDK 支持双缓冲(默认,高并发无锁)与锁模式(实现简单、内存更低),`counter-mode` 一键切换
- **内置监控指标**:服务端周期性采集 QPS、Pipeline 排队延迟、队列积压、连接数等,序列化推送至 Nacos
- **压测配套**:SDK 内基准程序与多 Worker 部署示例(docker-compose)

## 模块说明

| 模块 | 说明 |
|---|---|
| `hot-spotter-server` | 服务端:接收访问上报、滑动窗口聚合、热 Key 判定、结果推送、监控指标上报 |
| `hot-spotter-remoting` | 通信层:基于 Netty 的自定义协议,客户端/服务端通用的 Command 编解码与请求分发 |
| `hot-spotter-client-sdk` | 客户端 SDK(Spring Boot Starter):配置绑定、自动装配、访问统计上报、推送接收、本地缓存保护 |
| `hot-spotter-common` | 公共模块:通用模型与工具 |
| `hot-spotter-example` | 示例应用与性能基准(`example/perf/`) |

## 快速开始

### 构建

```bash
mvn clean install -DskipTests
```

### 启动服务端

服务端依赖 **Nacos**(客户端地址发现与监控数据下发)。入口类 `com.ispengya.hotkey.server.HotKeyServerApplication`:

```bash
java -jar hot-spotter-server/target/hot-spotter-server-*.jar
# 多实例/压测:通过系统属性覆盖配置
java -Dserver.port=8889 -jar hot-spotter-server/target/hot-spotter-server-*.jar
```

或使用 Docker 启动单 Worker(修改 `docker-compose.yml` 中 `nacos.serverAddr` 为你的地址):

```bash
docker-compose up -d
```

> 注:`Dockerfile` 以 `zookeeper` 镜像作为 JRE 基础镜像,属于压测环境的便捷做法,生产请使用自己的 JDK 基础镜像。

### 集成客户端 SDK

组件尚未发布到 Maven Central,请先在本地 `mvn install` 后引入:

```xml
<dependency>
    <groupId>com.ispengya</groupId>
    <artifactId>hot-spotter-client-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

启动类添加 `@EnableHotKey`:

```java
@EnableHotKey
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

业务侧通过 `HotKeyClient` 统一读写,热点 Key 自动进入本地缓存:

```java
@Service
public class DemoService {
    private final HotKeyClient hotKeyClient;

    public DemoService(HotKeyClient hotKeyClient) {
        this.hotKeyClient = hotKeyClient;
    }

    public String getValue(String key) {
        return hotKeyClient.get(key, this::loadFromDb);
    }
}
```

## 配置

### 服务端(hotkey-server.properties)

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

说明:

- 滑动时间窗口 ≈ `windowSizeMillis × windowSlotCount` ≈ 30 秒
- 最近窗口内访问次数 ≥ `minCountThreshold` 的 Key 判定为热 Key
- 热 Key 连续空闲超过 `hotKeyIdleMillis` 将被移除并下发冷却通知
- 上报数据按**服务端接收时间**落桶,减小网络延迟对统计的影响
- 任意配置均可通过 `-D` 系统属性覆盖(如 `-Dalgorithm.minCountThreshold=1000`)

**监控指标**:`MonitorReporter` 周期性采集 pipeline 接收/处理计数、热 Key 判定与推送计数、排队与加锁延迟、队列积压、连接数、窗口缓存大小等,计算 QPS 速率后以 JSON 推送至 Nacos,配合 `scripts/` 下的采样脚本可直接分析。

### 客户端(Spring Boot `hotkey` 前缀)

```yaml
hotkey:
  enabled: true
  app-name: hotkey-example
  client:
    server-addresses:
      - "127.0.0.1:8888"
    counter-mode: DOUBLE        # 计数采集模式:DOUBLE(默认)/ LOCK
    # connect-timeout-millis: 3000
    # report-pool-size: 2
    # local-cache-maximum-size: 1000
    # local-cache-expire-after-write-millis: 300000
```

计数采集模式对比(经 `perf` 基准测试验证):

| 模式 | 实现 | 特点 | 适用场景 |
|---|---|---|---|
| `DOUBLE` (默认) | 双缓冲 `DoubleBufferCounterCollector` | 读写缓冲分离,计数路径无锁,吞吐更高 | 高并发写热点计数 |
| `LOCK` | 加锁 `LockedCounterCollector` | 实现简单、常驻内存更低 | 中低并发或内存敏感 |

## 核心流程

1. **客户端统计**:`HotKeyClient` 统一入口,`HotKeyDetector` 记录访问并委托 `CacheTemplate` 加载
2. **批量上报**:通过 `hot-spotter-remoting` 长连接,定期批量上报 `AccessReport`
3. **写入滑动窗口**:`AccessReportPipeline` 消费上报,按应用选择窗口存储,以服务端接收时间落桶
4. **聚合判定**:`HotKeyComputeAlgorithm` 合并最近窗口切片,依阈值判定热 Key
5. **结果推送**:`HotKeyChangePublisher` 将热 Key 新增/移除实时下发
6. **本地生效**:客户端更新热 Key 集合,热点进入本地缓存保护
7. **衰减清理**:`HotKeyDecayTask` 清理长期不活跃的热 Key

## 压测与实验

- `hot-spotter-example/src/main/java/com/ispengya/hotkey/example/perf/`
  - `CollectorBenchmark`:两种计数模式在多线程下的吞吐/延迟对比
  - `SdkReportBenchmark`:SDK 上报链路基准
  - `PressureTest`:端到端压测入口
- `scripts/`:开发过程中的实验脚本(采样、多 Worker 对比等)。**脚本内含作者本地路径与 classpath 假设,直接复用前需按环境修改**

## License

[Apache License 2.0](LICENSE)
