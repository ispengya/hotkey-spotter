package com.ispengya.hotkey.example.perf;

import com.ispengya.hotkey.cli.detect.CounterMode;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;
import com.ispengya.hotkey.cli.rule.RuleHolder;
import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.rule.KeyRule;
import com.ispengya.hotkey.common.rule.RuleConfig;
import com.ispengya.hotkey.remoting.client.ClientRequestSender;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import com.ispengya.hotkey.remoting.client.NettyClient;
import com.ispengya.hotkey.remoting.event.HotKeyPushEvent;
import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.protocol.Serializer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 客户端 SDK 上报路径基准：单 map 锁 vs 双 map 无锁。
 *
 * <p>驱动<strong>真实的 SDK 上报链路</strong>：
 * {@code 业务线程 → HotKeyReporter.record() → RuleHolder.isInRule() → collector.record()
 * → report() 定时 drain → HotKeyRemotingClient.reportAccess() → Netty 上报 Worker}。</p>
 *
 * <p>除客户端 record 吞吐外，还测量<strong>端到端延迟</strong>：热点 key 从业务线程
 * 首次 {@code record()} 到客户端收到 Worker 推送（{@link HotKeyPushEvent}）的完整时延。
 * 该时延 = 聚合周期(drain) + 网络上报 + Worker 窗口判定 + push 回包，是热 key 保护
 * 最核心的指标。</p>
 *
 * <p>通过 {@link CounterMode} 切换 collector 实现（double / lock），在相同的并发
 * record + 周期 drain 负载下对比两者。可选 {@code --workers host:port} 连真实 Worker；
 * {@code --no-e2e} 关闭端到端延迟测量（纯测吞吐）。</p>
 *
 * @author ispengya
 */
public final class SdkReportBenchmark {

    private static final String APP_NAME = "perf-sdk";

    private static final class Config {
        int threads = 8;           // 业务并发线程数
        int keys = 50_000;         // 普通 key 基数（影响 drain 遍历时长）
        int hotKeys = 10;          // 热点 key 数
        double hotRatio = 0.9;     // 热点流量占比
        long reportPeriodMs = 500; // report()/drain 周期
        int duration = 10;         // 测量时长（秒）
        int warmup = 3;            // 预热时长（秒）
        String workers = null;     // 可选：真实 Worker 地址 "host:port"
        String mode = "both";      // lock | double | both（默认两者都测）
        boolean e2e = true;        // 是否测量端到端延迟
    }

    private static final class Result {
        String name;
        double opsPerSec;
        long totalRecords;
        long e2eCount;
        double e2eAvgMs, e2eP50Ms, e2eP90Ms, e2eP99Ms, e2eMaxMs;
    }

    /**
     * 端到端延迟收集器：每个 mode 独立一个实例，业务线程记首次访问时间，
     * push 监听线程算差值。当前活跃的 sink 由 {@link #CURRENT_SINK} 指向。
     */
    private static final class E2eSink {
        final ConcurrentHashMap<String, Long> pendingFirstSeen = new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        final LongAdder pushCount = new LongAdder();
    }

    private static final AtomicReference<E2eSink> CURRENT_SINK = new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        Config cfg = parseArgs(args);

        // —— 1. 组装真实 SDK 上报链路 ——
        EventBus eventBus = new EventBus();
        Serializer serializer = new Fastjson2Serializer();
        NettyClient nettyClient = new NettyClient(3000, 4, 1024 * 1024, eventBus, serializer);
        ClientRequestSender sender = new ClientRequestSender(nettyClient);
        HotKeyRemotingClient remotingClient = new HotKeyRemotingClient(serializer, sender, APP_NAME);

        // —— 2. 订阅 push 回包，计算端到端延迟 ——
        eventBus.register(HotKeyPushEvent.class, SdkReportBenchmark::onPush);

        if (cfg.workers != null && !cfg.workers.trim().isEmpty()) {
            nettyClient.start();
            List<InetSocketAddress> addrs = parseWorkers(cfg.workers);
            nettyClient.onWorkerChange(addrs);
        }

        // —— 3. 配规则：前缀 "*" 匹配所有 key，保证 record() 能进 collector ——
        KeyRule matchAll = new KeyRule("*", true, 60, 1_000_000, 60);
        RuleHolder.updateRuleConfig(APP_NAME, new RuleConfig(APP_NAME, Arrays.asList(matchAll)));

        printHeader(cfg);

        boolean runLock = !"double".equals(cfg.mode);
        boolean runDouble = !"lock".equals(cfg.mode);

        Result locked = runLock ? run(cfg, remotingClient, CounterMode.LOCK, "单 map + 锁") : null;
        if (runLock && runDouble) System.out.println();
        Result doubleMap = runDouble ? run(cfg, remotingClient, CounterMode.DOUBLE, "双 map 无锁") : null;

        if (locked != null && doubleMap != null) {
            printSummary(locked, doubleMap);
        } else {
            System.out.println();
            System.out.println("单模式运行完成（--mode=" + cfg.mode + "），未做双实现对比。");
        }

        nettyClient.stop();
        System.exit(0);
    }

    private static void onPush(HotKeyPushEvent event) {
        E2eSink sink = CURRENT_SINK.get();
        if (sink == null) {
            return;
        }
        HotKeyViewMessage message = event.getMessage();
        if (message == null || message.getViews() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (HotKeyViewMessage.ViewEntry entry : message.getViews().values()) {
            if (entry == null || entry.getAddedKey() == null) {
                continue;
            }
            Long firstSeen = sink.pendingFirstSeen.remove(entry.getAddedKey());
            if (firstSeen != null) {
                sink.latencies.offer(now - firstSeen);
                sink.pushCount.increment();
            }
        }
    }

    private static Result run(Config cfg, HotKeyRemotingClient remotingClient,
                              CounterMode mode, String name) throws Exception {
        HotKeyReporter reporter = new HotKeyReporter(remotingClient, APP_NAME, cfg.reportPeriodMs, mode);
        reporter.start();

        String[] normalKeys = new String[cfg.keys];
        for (int i = 0; i < cfg.keys; i++) normalKeys[i] = "k-" + i;
        String[] hotKeySet = new String[cfg.hotKeys];
        for (int i = 0; i < cfg.hotKeys; i++) hotKeySet[i] = "hot-" + i;

        E2eSink sink = cfg.e2e ? new E2eSink() : null;
        if (sink != null) {
            CURRENT_SINK.set(sink);
            // 预注册所有热点 key 的首访时间（在写入开始前），
            // 避免在写入热路径上对每个热点记录做 putIfAbsent 污染吞吐。
            long t0 = System.currentTimeMillis();
            for (String k : hotKeySet) {
                sink.pendingFirstSeen.put(k, t0);
            }
        }

        AtomicBoolean running = new AtomicBoolean(true);
        LongAdder totalRecords = new LongAdder();

        List<Thread> writers = new ArrayList<>();
        for (int i = 0; i < cfg.threads; i++) {
            Thread t = new Thread(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                while (running.get()) {
                    boolean hot = rnd.nextDouble() < cfg.hotRatio;
                    String key = hot ? hotKeySet[rnd.nextInt(cfg.hotKeys)]
                                     : normalKeys[rnd.nextInt(cfg.keys)];
                    reporter.record(key);
                    totalRecords.increment();
                }
            }, "sdk-writer-" + i);
            t.setDaemon(true);
            writers.add(t);
        }

        writers.forEach(Thread::start);

        // 预热
        Thread.sleep(cfg.warmup * 1000L);
        long startNanos = System.nanoTime();
        long startRecords = totalRecords.sum();

        // 测量
        Thread.sleep(cfg.duration * 1000L);
        running.set(false);

        long elapsedNanos = System.nanoTime() - startNanos;
        for (Thread t : writers) t.join(2000);
        reporter.stop();

        Result r = new Result();
        r.name = name;
        r.opsPerSec = (totalRecords.sum() - startRecords) * 1e9 / elapsedNanos;
        r.totalRecords = totalRecords.sum() - startRecords;

        System.out.printf("[%s]  record=%,.0f/s  (measure total=%,d, period=%dms)%n",
                name, r.opsPerSec, r.totalRecords, cfg.reportPeriodMs);

        if (sink != null) {
            // 等在途 push 回包到达，再停止收集
            Thread.sleep(1000);
            CURRENT_SINK.set(null);
            fillE2e(r, sink);
            if (r.e2eCount > 0) {
                System.out.printf("[%s]  e2e延迟: count=%d avg=%.0fms p50=%.0fms p90=%.0fms p99=%.0fms max=%.0fms%n",
                        name, r.e2eCount, r.e2eAvgMs, r.e2eP50Ms, r.e2eP90Ms, r.e2eP99Ms, r.e2eMaxMs);
            } else {
                System.out.printf("[%s]  e2e延迟: 无样本（未收到 push 回包）%n", name);
            }
        }
        return r;
    }

    private static void fillE2e(Result r, E2eSink sink) {
        List<Long> l = new ArrayList<>(sink.latencies);
        int n = l.size();
        r.e2eCount = n;
        if (n == 0) return;
        Collections.sort(l);
        r.e2eAvgMs = l.stream().mapToLong(Long::longValue).sum() / (double) n;
        r.e2eP50Ms = l.get(Math.min(n - 1, (int) (n * 0.50)));
        r.e2eP90Ms = l.get(Math.min(n - 1, (int) (n * 0.90)));
        r.e2eP99Ms = l.get(Math.min(n - 1, (int) (n * 0.99)));
        r.e2eMaxMs = l.get(n - 1);
    }

    private static void printSummary(Result locked, Result doubleMap) {
        double speedup = doubleMap.opsPerSec / locked.opsPerSec;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SDK 上报吞吐对比                                              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-18s %,20.0f /s ║%n", "单 map 锁 record:", locked.opsPerSec);
        System.out.printf("║  %-18s %,20.0f /s ║%n", "双 map 无锁 record:", doubleMap.opsPerSec);
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  双 map 写入吞吐 = 单 map 锁 ×%.2f                               ║%n", speedup);
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        if (locked.e2eCount > 0 && doubleMap.e2eCount > 0) {
            System.out.println();
            System.out.println("  端到端延迟对比（首次 record → 收到 push 回包）：");
            System.out.printf("    单 map 锁  : count=%d  avg=%.0fms  p99=%.0fms%n",
                    locked.e2eCount, locked.e2eAvgMs, locked.e2eP99Ms);
            System.out.printf("    双 map 无锁: count=%d  avg=%.0fms  p99=%.0fms%n",
                    doubleMap.e2eCount, doubleMap.e2eAvgMs, doubleMap.e2eP99Ms);
            System.out.println("    （两者理论上应一致——collector 只影响客户端聚合，不影响端到端链路）");
        }

        System.out.println();
        System.out.println("说明：");
        System.out.println("  - record 吞吐是业务线程调用 SDK record() 的稳态速率，即客户端能吸收多少访问量。");
        System.out.println("  - 单 map 锁把 N 个业务线程串行化在同一把 ReentrantLock 上（等效单线程）；");
        System.out.println("    双 map 靠 ConcurrentHashMap 分段让多线程并行，因此吞吐随并发线程数成倍提升。");
        System.out.println("  - 端到端延迟 = 首次 record 到收到 push 回包，含聚合周期 + 网络 + Worker 判定 + 回包；");
        System.out.println("    它不受 collector 实现影响，两种模式应一致。");
        System.out.println("  - 热点 key 受 Worker 去重缓存（1min）约束，每个 key 只推一次，故 e2e 样本数 ≈ hotKeys。");
        System.out.println("    要更多样本请调大 --hot-keys。");
    }

    private static List<InetSocketAddress> parseWorkers(String spec) {
        List<InetSocketAddress> list = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] hp = part.trim().split(":");
            if (hp.length == 2) {
                list.add(new InetSocketAddress(hp[0].trim(), Integer.parseInt(hp[1].trim())));
            }
        }
        return list.isEmpty() ? Collections.emptyList() : list;
    }

    private static void printHeader(Config cfg) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SDK Report Benchmark — 单 map 锁 vs 双 map 无锁              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  threads=%-4d keys=%,-10d hotKeys=%-4d hotRatio=%.0f%%      ║%n",
                cfg.threads, cfg.keys, cfg.hotKeys, cfg.hotRatio * 100);
        System.out.printf("║  reportPeriod=%-4dms warmup=%-3ds duration=%-3ds            ║%n",
                cfg.reportPeriodMs, cfg.warmup, cfg.duration);
        System.out.printf("║  mode=%-52s ║%n", cfg.mode);
        System.out.printf("║  e2e=%-53s ║%n", cfg.e2e ? "on" : "off");
        System.out.printf("║  workers=%-50s ║%n", cfg.workers == null ? "(none, 本地聚合)" : cfg.workers);
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");
    }

    private static Config parseArgs(String[] args) {
        Config cfg = new Config();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--threads":          cfg.threads = Integer.parseInt(args[++i]); break;
                case "--keys":             cfg.keys = Integer.parseInt(args[++i]); break;
                case "--hot-keys":         cfg.hotKeys = Integer.parseInt(args[++i]); break;
                case "--hot-ratio":        cfg.hotRatio = Double.parseDouble(args[++i]); break;
                case "--report-period-ms": cfg.reportPeriodMs = Long.parseLong(args[++i]); break;
                case "--duration":         cfg.duration = Integer.parseInt(args[++i]); break;
                case "--warmup":           cfg.warmup = Integer.parseInt(args[++i]); break;
                case "--workers":          cfg.workers = args[++i]; break;
                case "--mode":             cfg.mode = args[++i]; break;
                case "--no-e2e":           cfg.e2e = false; break;
                default:
                    System.err.printf("Unknown: %s%n", args[i]);
                    System.exit(1);
            }
        }
        return cfg;
    }
}
