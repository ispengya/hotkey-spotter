package com.ispengya.hotkey.example.perf;

import com.ispengya.hotkey.remoting.codec.CommandDecoder;
import com.ispengya.hotkey.remoting.codec.CommandEncoder;
import com.ispengya.hotkey.remoting.message.AccessReportMessage;
import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 热 Key 探测系统压测工具 v3 — 支持多 Worker 目标。
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>全局令牌桶限速（--rate keys/s）</li>
 *   <li>多 Worker 目标（--workers host1:port1,host2:port2）</li>
 *   <li>连接均匀分配到各 Worker</li>
 *   <li>writeAndFlush 回调计数（准确）</li>
 *   <li>支持 --find-max 自动饱和探测</li>
 * </ul>
 *
 * @author ispengya
 */
public final class PressureTest {

    private static final class Config {
        String workers = "127.0.0.1:8888";  // "host1:port1,host2:port2,..."
        int keys = 5000;
        int hotKeys = 50;
        double hotRatio = 0.8;
        int conns = 5;
        int duration = 30;
        int batchSize = 200;
        long rate = 0;          // 0 = unlimited
        boolean findMax = false;
        boolean pushStress = false;  // 推送压测模式：用动态 key 名绕过去重
    }

    private static final class WorkerTarget {
        final String host;
        final int port;
        final String label;

        WorkerTarget(String host, int port) {
            this.host = host;
            this.port = port;
            this.label = host + ":" + port;
        }
    }

    // ==================== 统计 ====================

    /** 实际进入 Netty pipeline 的 key 数量（writeAndFlush 回调中计数） */
    private static final LongAdder actuallyFlushed = new LongAdder();
    private static final LongAdder totalPushed = new LongAdder();

    /** 待推送延迟追踪 */
    private static final ConcurrentHashMap<String, Long> pendingPushes = new ConcurrentHashMap<>();
    private static final int MAX_PENDING = 100_000;
    private static final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
    private static final int MAX_LATENCY_SAMPLES = 50_000;

    private static final AtomicLong lastFlushedSnapshot = new AtomicLong();
    private static final AtomicLong lastPushSnapshot = new AtomicLong();
    private static final AtomicBoolean running = new AtomicBoolean(true);

    // ==================== 令牌桶（Semaphore 实现） ====================

    private static final class TokenBucket {
        private final java.util.concurrent.Semaphore semaphore;
        private final long ratePerSec;
        private final ScheduledExecutorService refiller;

        TokenBucket(long ratePerSec) {
            this.ratePerSec = ratePerSec;
            this.semaphore = new java.util.concurrent.Semaphore((int) Math.min(ratePerSec, Integer.MAX_VALUE));
            this.refiller = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "token-refiller");
                t.setDaemon(true);
                return t;
            });
            // 每 100ms 补充一次令牌
            long refillIntervalMs = 100;
            long tokensPerRefill = ratePerSec * refillIntervalMs / 1000;
            refiller.scheduleAtFixedRate(
                    () -> semaphore.release((int) Math.min(tokensPerRefill, Integer.MAX_VALUE - semaphore.availablePermits())),
                    refillIntervalMs, refillIntervalMs, TimeUnit.MILLISECONDS);
        }

        boolean tryConsume(long n) {
            return semaphore.tryAcquire((int) n);
        }

        void acquire(long n) throws InterruptedException {
            semaphore.acquire((int) n);
        }

        void shutdown() {
            refiller.shutdown();
        }
    }

    // ==================== main ====================

    public static void main(String[] args) throws Exception {
        Config cfg = parseArgs(args);
        Serializer serializer = new Fastjson2Serializer();

        // 解析 Worker 目标列表
        List<WorkerTarget> targets = parseWorkers(cfg.workers);

        String[] normalKeys = new String[cfg.keys];
        for (int i = 0; i < cfg.keys; i++) normalKeys[i] = "k-" + i;
        String[] hotKeySet = new String[cfg.hotKeys];
        for (int i = 0; i < cfg.hotKeys; i++) hotKeySet[i] = "hot-" + i;

        if (cfg.findMax) {
            runFindMax(cfg, serializer, targets, normalKeys, hotKeySet);
        } else {
            runFixed(cfg, serializer, targets, normalKeys, hotKeySet);
        }
        System.exit(0);
    }

    private static List<WorkerTarget> parseWorkers(String spec) {
        List<WorkerTarget> list = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] hp = part.trim().split(":");
            if (hp.length == 2) {
                list.add(new WorkerTarget(hp[0].trim(), Integer.parseInt(hp[1].trim())));
            }
        }
        if (list.isEmpty()) {
            list.add(new WorkerTarget("127.0.0.1", 8888));
        }
        return list;
    }

    // ==================== Fixed Rate ====================

    private static void runFixed(Config cfg, Serializer serializer,
                                  List<WorkerTarget> targets,
                                  String[] normalKeys, String[] hotKeySet) throws Exception {
        TokenBucket bucket = cfg.rate > 0 ? new TokenBucket(cfg.rate) : null;

        printHeader(cfg, targets, bucket);
        List<Channel> channels = connect(cfg, targets, serializer);

        ScheduledExecutorService statsPrinter = Executors.newSingleThreadScheduledExecutor();
        statsPrinter.scheduleAtFixedRate(PressureTest::printStats, 1, 1, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis() + cfg.duration * 1000L;
        CountDownLatch doneSignal = new CountDownLatch(cfg.conns);

        for (int idx = 0; idx < channels.size(); idx++) {
            Channel ch = channels.get(idx);
            Thread t = new Thread(() -> sendLoop(ch, normalKeys, hotKeySet, cfg, serializer, bucket, endTime, doneSignal),
                    "perf-sender-" + idx);
            t.setDaemon(false);
            t.start();
        }

        doneSignal.await();
        Thread.sleep(1500);
        running.set(false);
        statsPrinter.shutdown();

        // 关闭连接
        for (Channel ch : channels) { try { ch.close().sync(); } catch (Exception ignored) {} }

        printFinalStats(cfg, targets);
    }

    // ==================== Find Max Mode ====================

    private static void runFindMax(Config cfg, Serializer serializer,
                                    List<WorkerTarget> targets,
                                    String[] normalKeys, String[] hotKeySet) throws Exception {
        long rate = cfg.rate > 0 ? cfg.rate : 50_000;  // 默认从 5万/s 起步
        int stage = 0;

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  Find Max Mode — auto-ramp to saturation         ║");
        System.out.printf("║  workers: %d, start: %,d/s, step: 2x          ║%n",
                targets.size(), rate);
        System.out.printf("║  stop when: flush << rate or avg latency > 500ms ║%n");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        while (true) {
            stage++;
            // 重置统计
            actuallyFlushed.reset();
            totalPushed.reset();
            pendingPushes.clear();
            latencies.clear();
            lastFlushedSnapshot.set(0);
            lastPushSnapshot.set(0);
            running.set(true);

            TokenBucket bucket = new TokenBucket(rate);
            System.out.printf("[Stage %d] rate=%,d/s, duration=%ds ...%n", stage, rate, cfg.duration);

            List<Channel> channels = connect(cfg, targets, serializer);

            ScheduledExecutorService statsPrinter = Executors.newSingleThreadScheduledExecutor();
            statsPrinter.scheduleAtFixedRate(PressureTest::printStats, 2, 2, TimeUnit.SECONDS);

            long endTime = System.currentTimeMillis() + cfg.duration * 1000L;
            CountDownLatch doneSignal = new CountDownLatch(cfg.conns);

            for (int idx = 0; idx < channels.size(); idx++) {
                Channel ch = channels.get(idx);
                Thread t = new Thread(() -> sendLoop(ch, normalKeys, hotKeySet, cfg, serializer, bucket, endTime, doneSignal),
                        "perf-sender-" + idx);
                t.setDaemon(false);
                t.start();
            }

            doneSignal.await();
            Thread.sleep(1500);
            running.set(false);
            statsPrinter.shutdown();

            for (Channel ch : channels) { try { ch.close().sync(); } catch (Exception ignored) {} }

            long flushed = actuallyFlushed.sum();
            long rateAchieved = flushed / cfg.duration;
            int latCount = latencies.size();
            long avgLat = 0;
            if (latCount > 0) {
                avgLat = latencies.stream().mapToLong(Long::longValue).sum() / latCount;
            }

            // 判断饱和：实际 flush 远小于设定 rate（给 70% 容差）
            double achievement = rate > 0 ? (double) rateAchieved / rate : 1.0;
            boolean saturated = achievement < 0.70
                    || (avgLat > 500)
                    || (rate > 5_000_000);

            System.out.printf("  → actual=%,d/s, pushed=%d, avgLat=%dms, samples=%d %s%n%n",
                    rateAchieved, totalPushed.sum(), avgLat, latCount,
                    saturated ? "⚡ SATURATED" : "✓ OK");

            if (saturated) {
                System.out.printf(">>> Saturation point: ~%,d keys/s (stage %d, %d workers)%n",
                        rateAchieved, stage, targets.size());
                break;
            }

            rate *= 2;
            Thread.sleep(3000); // 让 Worker 喘口气
        }
    }

    // ==================== 连接 ====================

    private static List<Channel> connect(Config cfg, List<WorkerTarget> targets,
                                          Serializer serializer) throws InterruptedException {
        System.out.printf("[PressureTest] Connecting %d conns to %d worker(s)...",
                cfg.conns, targets.size());
        List<Channel> channels = new ArrayList<>(cfg.conns);
        for (int i = 0; i < cfg.conns; i++) {
            // 轮询分配到各 Worker
            WorkerTarget target = targets.get(i % targets.size());

            EventLoopGroup g = new NioEventLoopGroup(2);
            Bootstrap b = new Bootstrap();
            b.group(g).channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new CommandEncoder())
                                    .addLast(new CommandDecoder(1024 * 1024))
                                    .addLast(new PushHandler(serializer));
                        }
                    });
            ChannelFuture f = b.connect(target.host, target.port).sync();
            channels.add(f.channel());
        }
        System.out.printf(" %d established.%n", channels.size());
        return channels;
    }

    // ==================== 发送线程 ====================

    private static void sendLoop(Channel ch, String[] normalKeys, String[] hotKeySet,
                                  Config cfg, Serializer serializer,
                                  TokenBucket bucket, long endTime, CountDownLatch doneSignal) {
        try {
            int batchCounter = 0;
            while (running.get() && System.currentTimeMillis() < endTime && ch.isActive()) {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();

                // push-stress: 每 5 个 batch 换一次 suffix，确保同一 key 累积 ≥3 次触发判定
                int suffix = batchCounter / 5;
                batchCounter++;

                Map<String, Integer> counts = new HashMap<>(cfg.batchSize);
                for (int j = 0; j < cfg.batchSize; j++) {
                    if (rnd.nextDouble() < cfg.hotRatio) {
                        String baseKey = hotKeySet[rnd.nextInt(cfg.hotKeys)];
                        String key = cfg.pushStress
                                ? baseKey + "-" + suffix
                                : baseKey;
                        counts.merge(key, 1, Integer::sum);
                    } else {
                        counts.merge(normalKeys[rnd.nextInt(cfg.keys)], 1, Integer::sum);
                    }
                }

                // 令牌桶限速
                if (bucket != null) {
                    bucket.acquire(cfg.batchSize);
                }

                AccessReportMessage msg = new AccessReportMessage();
                msg.setAppName("perf-test");
                msg.setTimestamp(System.currentTimeMillis());
                msg.setKeyAccessCounts(counts);

                byte[] payload = serializer.serialize(msg);
                Command cmd = new Command(CommandType.ACCESS_REPORT, payload);

                long now = System.currentTimeMillis();
                for (Map.Entry<String, Integer> e : counts.entrySet()) {
                    if (e.getKey().startsWith("hot-") && pendingPushes.size() < MAX_PENDING) {
                        pendingPushes.put(e.getKey(), now);
                    }
                }

                // writeAndFlush 回调中计数（确保真的 flush 进了 Netty pipeline）
                ch.writeAndFlush(cmd).addListener(f -> {
                    if (f.isSuccess()) {
                        actuallyFlushed.add(counts.size());
                    }
                });
            }
        } catch (Exception e) {
            System.err.printf("[sender] Error: %s%n", e.getMessage());
        } finally {
            doneSignal.countDown();
        }
    }

    // ==================== Push 处理 ====================

    private static final class PushHandler extends SimpleChannelInboundHandler<Command> {
        private final Serializer serializer;
        PushHandler(Serializer s) { this.serializer = s; }

        protected void channelRead0(ChannelHandlerContext ctx, Command cmd) {
            if (cmd.getType() != CommandType.HOT_KEY_PUSH) return;
            totalPushed.increment();
            try {
                HotKeyViewMessage msg = serializer.deserialize(cmd.getPayload(), HotKeyViewMessage.class);
                if (msg == null || msg.getViews() == null) return;
                long now = System.currentTimeMillis();
                for (Map.Entry<String, HotKeyViewMessage.ViewEntry> e : msg.getViews().entrySet()) {
                    HotKeyViewMessage.ViewEntry ve = e.getValue();
                    if (ve == null || ve.getAddedKey() == null) continue;
                    Long sendTime = pendingPushes.remove(ve.getAddedKey());
                    if (sendTime != null && sendTime > 0 && latencies.size() < MAX_LATENCY_SAMPLES) {
                        latencies.offer(now - sendTime);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // ==================== 统计 ====================

    private static void printStats() {
        long curFlushed = actuallyFlushed.sum();
        long curPushed = totalPushed.sum();
        long prevFlush = lastFlushedSnapshot.getAndSet(curFlushed);
        long prevPush = lastPushSnapshot.getAndSet(curPushed);

        long flushQps = curFlushed - prevFlush;
        long pushQps = curPushed - prevPush;
        int latCount = latencies.size();
        long avgLat = 0;
        if (latCount > 0) avgLat = latencies.stream().mapToLong(Long::longValue).sum() / latCount;

        System.out.printf("%s  flush=%,d/s (%,d) | push=%d/s (%d) | lat=%dms | samples=%d%n",
                java.time.LocalTime.now().toString().substring(0, 8),
                flushQps, curFlushed, pushQps, curPushed, avgLat, latCount);
    }

    private static void printFinalStats(Config cfg, List<WorkerTarget> targets) {
        long flushed = actuallyFlushed.sum();
        long pushed = totalPushed.sum();
        int count = latencies.size();

        System.out.printf("%n╔══════════════════════════════════════════════════════╗%n");
        System.out.printf("║  Final Results (v3 — multi-worker)                  ║%n");
        System.out.printf("╠══════════════════════════════════════════════════════╣%n");
        System.out.printf("║  %-20s %,2d workers              ║%n", "Workers:", targets.size());
        for (WorkerTarget t : targets) {
            System.out.printf("║    - %-32s ║%n", t.label);
        }
        if (cfg.rate > 0) System.out.printf("║  %-20s %,12d /s            ║%n", "Target rate:", cfg.rate);
        System.out.printf("║  %-20s %,12d /s            ║%n", "Actual flush:", flushed / cfg.duration);
        System.out.printf("║  %-20s %,12d              ║%n", "Total flushed:", flushed);
        System.out.printf("║  %-20s %,12d              ║%n", "Total push:", pushed);
        System.out.printf("║  %-20s %,12d              ║%n", "Latency samples:", count);

        if (count > 0) {
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            long p50 = sorted.get(count / 2);
            long p90 = sorted.get((int) (count * 0.90));
            long p99 = sorted.get((int) (count * 0.99));
            long avg = sorted.stream().mapToLong(Long::longValue).sum() / count;
            System.out.printf("║  %-20s %,12d ms            ║%n", "Avg:", avg);
            System.out.printf("║  %-20s %,12d ms            ║%n", "P50:", p50);
            System.out.printf("║  %-20s %,12d ms            ║%n", "P90:", p90);
            System.out.printf("║  %-20s %,12d ms            ║%n", "P99:", p99);
        }
        System.out.printf("╚══════════════════════════════════════════════════════╝%n");
    }

    private static void printHeader(Config cfg, List<WorkerTarget> targets, TokenBucket bucket) {
        System.out.printf("╔══════════════════════════════════════════════════════╗%n");
        System.out.printf("║  HotSpotter Pressure Test v3 — Multi-Worker          ║%n");
        System.out.printf("╠══════════════════════════════════════════════════════╣%n");
        System.out.printf("║  %-20s %d workers%n", "targets:", targets.size());
        for (WorkerTarget t : targets) {
            System.out.printf("║    - %-32s%n", t.label);
        }
        System.out.printf("║  %-20s %d + %d hot%n", "keys:", cfg.keys, cfg.hotKeys);
        System.out.printf("║  %-20s %.0f%%%n", "hotRatio:", cfg.hotRatio * 100);
        System.out.printf("║  %-20s %d  (batch=%d)%n", "conns:", cfg.conns, cfg.batchSize);
        System.out.printf("║  %-20s %ds%n", "duration:", cfg.duration);
        System.out.printf("║  %-20s %,d /s%n", "rate limit:", bucket != null ? cfg.rate : 0);
        if (cfg.pushStress) System.out.printf("║  %-20s ON (rotating keys)%n", "push stress:");
        System.out.printf("╚══════════════════════════════════════════════════════╝%n");
    }

    // ==================== 参数解析 ====================

    private static Config parseArgs(String[] args) {
        Config cfg = new Config();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--workers":    cfg.workers = args[++i]; break;
                case "--host": {
                    String host = args[++i];
                    int port = 8888;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try { port = Integer.parseInt(args[++i]); } catch (NumberFormatException e) { --i; }
                    }
                    cfg.workers = host + ":" + port;
                    break;
                }
                case "--port":       { /* deprecated, use --workers or --host host port */ ++i; break; }
                case "--keys":       cfg.keys = Integer.parseInt(args[++i]); break;
                case "--hot-keys":   cfg.hotKeys = Integer.parseInt(args[++i]); break;
                case "--hot-ratio":  cfg.hotRatio = Double.parseDouble(args[++i]); break;
                case "--conns":      cfg.conns = Integer.parseInt(args[++i]); break;
                case "--duration":   cfg.duration = Integer.parseInt(args[++i]); break;
                case "--batch-size": cfg.batchSize = Integer.parseInt(args[++i]); break;
                case "--rate":       cfg.rate = Long.parseLong(args[++i]); break;
                case "--find-max":   cfg.findMax = true; break;
                case "--push-stress": cfg.pushStress = true; break;
                default:
                    System.err.printf("Unknown: %s%n", args[i]);
                    System.exit(1);
            }
        }
        return cfg;
    }
}
