package com.ispengya.hotkey.example.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 访问计数器实现对比基准：单 map + 锁 vs 双 map 无锁。
 *
 * <p>复刻了生产 {@code AccessCounterCollector} 的双 map 无锁实现（<em>双缓冲</em>），
 * 以及与它语义等价的「一个 HashMap + ReentrantLock」实现（<em>单 map 锁</em>）。
 * 两者暴露同一接口 {@link Collector}，在相同的并发 record + 周期 drain 负载下测量
 * 写入吞吐和 drain 延迟，从而量化「双 map 相对单 map 锁的提升」。</p>
 *
 * <h3>关键差异</h3>
 * <ul>
 *   <li>单 map 锁：{@code drain()} 持有锁遍历整个 map 期间，所有写入线程被阻塞。</li>
 *   <li>双 map 无锁：{@code drain()} 只翻转序号并清空另一个 map，写入线程始终不阻塞。</li>
 * </ul>
 * <p>因此双 map 的收益会随 key 基数（drain 遍历时长）和写入并发度放大。</p>
 *
 * @author ispengya
 */
public final class CollectorBenchmark {

    private interface Collector {
        void record(String key);

        Map<String, Integer> drain();
    }

    // ==================== 双 map 无锁（复刻 AccessCounterCollector） ====================

    private static final class DoubleMapCollector implements Collector {
        private final ConcurrentHashMap<String, LongAdder> map0 = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, LongAdder> map1 = new ConcurrentHashMap<>();
        private final AtomicLong seq = new AtomicLong();

        @Override
        public void record(String key) {
            if ((seq.get() & 1L) == 0L) {
                map0.computeIfAbsent(key, k -> new LongAdder()).increment();
            } else {
                map1.computeIfAbsent(key, k -> new LongAdder()).increment();
            }
        }

        @Override
        public Map<String, Integer> drain() {
            long v = seq.incrementAndGet();
            ConcurrentHashMap<String, LongAdder> target = (v & 1L) == 0L ? map1 : map0;
            Map<String, Integer> result = new HashMap<>();
            target.forEach((key, adder) -> {
                long count = adder.sumThenReset();
                if (count > 0L) {
                    result.put(key, (int) count);
                }
            });
            target.entrySet().removeIf(e -> e.getValue().sum() == 0L);
            return result;
        }
    }

    // ==================== 单 map + 锁 ====================

    private static final class LockedSingleMapCollector implements Collector {
        private final Map<String, LongAdder> map = new HashMap<>();
        private final ReentrantLock lock = new ReentrantLock();

        @Override
        public void record(String key) {
            lock.lock();
            try {
                map.computeIfAbsent(key, k -> new LongAdder()).increment();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Map<String, Integer> drain() {
            lock.lock();
            try {
                Map<String, Integer> result = new HashMap<>(map.size());
                for (Map.Entry<String, LongAdder> e : map.entrySet()) {
                    long count = e.getValue().sumThenReset();
                    if (count > 0L) {
                        result.put(e.getKey(), (int) count);
                    }
                }
                map.entrySet().removeIf(e -> e.getValue().sum() == 0L);
                return result;
            } finally {
                lock.unlock();
            }
        }
    }

    // ==================== 配置与结果 ====================

    private static final class Config {
        int threads = 8;          // 写入线程数
        int keys = 50_000;        // 普通 key 基数（影响 drain 遍历时长）
        int hotKeys = 10;         // 热点 key 数
        double hotRatio = 0.9;    // 热点 key 流量占比
        int drainMs = 100;        // drain 周期
        int duration = 10;        // 测量时长（秒）
        int warmup = 3;           // 预热时长（秒）
    }

    private static final class Result {
        String name;
        double opsPerSec;
        long drains;
        double drainAvgMs;
        double drainP50Ms;
        double drainP99Ms;
        double drainMaxMs;
    }

    // ==================== main ====================

    public static void main(String[] args) throws Exception {
        Config cfg = parseArgs(args);

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Collector Benchmark — 单 map 锁 vs 双 map 无锁              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  threads=%-4d keys=%,-10d hotKeys=%-4d hotRatio=%.0f%%      ║%n",
                cfg.threads, cfg.keys, cfg.hotKeys, cfg.hotRatio * 100);
        System.out.printf("║  drain=%-4dms warmup=%-3ds duration=%-3ds                   ║%n",
                cfg.drainMs, cfg.warmup, cfg.duration);
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        Result locked = run(new LockedSingleMapCollector(), cfg, "单 map + 锁");
        System.out.println();
        Result doubleMap = run(new DoubleMapCollector(), cfg, "双 map 无锁");

        printSummary(cfg, locked, doubleMap);
    }

    // ==================== 单次基准运行 ====================

    private static Result run(Collector collector, Config cfg, String name) throws Exception {
        String[] normalKeys = new String[cfg.keys];
        for (int i = 0; i < cfg.keys; i++) normalKeys[i] = "k-" + i;
        String[] hotKeySet = new String[cfg.hotKeys];
        for (int i = 0; i < cfg.hotKeys; i++) hotKeySet[i] = "hot-" + i;

        AtomicBoolean running = new AtomicBoolean(true);
        LongAdder totalRecords = new LongAdder();
        ConcurrentLinkedQueue<Long> drainNanos = new ConcurrentLinkedQueue<>();
        AtomicLong drains = new AtomicLong();

        List<Thread> writers = new ArrayList<>();
        for (int i = 0; i < cfg.threads; i++) {
            Thread t = new Thread(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                while (running.get()) {
                    String key = rnd.nextDouble() < cfg.hotRatio
                            ? hotKeySet[rnd.nextInt(cfg.hotKeys)]
                            : normalKeys[rnd.nextInt(cfg.keys)];
                    collector.record(key);
                    totalRecords.increment();
                }
            }, "collector-writer-" + i);
            t.setDaemon(true);
            writers.add(t);
        }

        Thread drainer = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(cfg.drainMs);
                } catch (InterruptedException e) {
                    break;
                }
                long t0 = System.nanoTime();
                collector.drain();
                long t1 = System.nanoTime();
                drainNanos.offer(t1 - t0);
                drains.incrementAndGet();
            }
        }, "collector-drainer");
        drainer.setDaemon(true);

        writers.forEach(Thread::start);
        drainer.start();

        // 预热
        Thread.sleep(cfg.warmup * 1000L);
        long startNanos = System.nanoTime();
        long startRecords = totalRecords.sum();

        // 测量
        Thread.sleep(cfg.duration * 1000L);
        running.set(false);
        drainer.interrupt();

        long elapsedNanos = System.nanoTime() - startNanos;
        for (Thread t : writers) t.join(2000);
        drainer.join(2000);

        double opsPerSec = (totalRecords.sum() - startRecords) * 1e9 / elapsedNanos;

        List<Long> sorted = new ArrayList<>(drainNanos);
        Collections.sort(sorted);
        int n = sorted.size();
        double p50 = percentile(sorted, 0.50);
        double p99 = percentile(sorted, 0.99);
        double avg = sorted.stream().mapToLong(Long::longValue).sum() / (double) Math.max(1, n);
        double max = n > 0 ? sorted.get(n - 1) / 1e6 : 0;

        Result r = new Result();
        r.name = name;
        r.opsPerSec = opsPerSec;
        r.drains = drains.get();
        r.drainAvgMs = avg / 1e6;
        r.drainP50Ms = p50 / 1e6;
        r.drainP99Ms = p99 / 1e6;
        r.drainMaxMs = max;

        System.out.printf("[%s]  record=%,.0f/s  drains=%d  drain: avg=%.3fms p50=%.3fms p99=%.3fms max=%.3fms%n",
                name, opsPerSec, r.drains, r.drainAvgMs, r.drainP50Ms, r.drainP99Ms, r.drainMaxMs);
        return r;
    }

    private static double percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int idx = Math.min(sorted.size() - 1, (int) (p * sorted.size()));
        return sorted.get(idx);
    }

    // ==================== 汇总 ====================

    private static void printSummary(Config cfg, Result locked, Result doubleMap) {
        double speedup = doubleMap.opsPerSec / locked.opsPerSec;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  对比汇总                                                             ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-16s %18s %18s ║%n", "指标", "单 map 锁", "双 map 无锁");
        System.out.printf("║  %-16s %,18.0f %,18.0f ║%n", "record (ops/s)", locked.opsPerSec, doubleMap.opsPerSec);
        System.out.printf("║  %-16s %,18.3f %,18.3f ║%n", "drain p99 (ms)", locked.drainP99Ms, doubleMap.drainP99Ms);
        System.out.printf("║  %-16s %,18.3f %,18.3f ║%n", "drain max (ms)", locked.drainMaxMs, doubleMap.drainMaxMs);
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  双 map 写入吞吐 = 单 map 锁 ×%.2f                                     ║%n", speedup);
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

        System.out.println();
        System.out.println("说明：");
        System.out.println("  - record 吞吐是写入端在「持续 drain」下的稳态吞吐，这是本次对比的核心指标：");
        System.out.println("    单 map 锁把所有写线程串行化在同一把锁上（等效单线程）；双 map 无锁则靠");
        System.out.println("    ConcurrentHashMap 分段让多线程并行，因此吞吐成倍提升。");
        System.out.println("  - drain 本身耗时两者相近（双 map 略慢，因无锁版做 forEach+removeIf 两次遍历），");
        System.out.println("    但关键区别是：单 map 锁在 drain 期间阻塞所有写入；双 map 全程不阻塞写入。");
        System.out.println("  - 收益随 key 基数(--keys)与写入并发(--threads)增大而更明显；");
        System.out.println("    当 key 基数很小、drain 遍历极快时，单 map 锁反而可能略快（省去 CHM 段开销）。");
    }

    // ==================== 参数解析 ====================

    private static Config parseArgs(String[] args) {
        Config cfg = new Config();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--threads":    cfg.threads = Integer.parseInt(args[++i]); break;
                case "--keys":       cfg.keys = Integer.parseInt(args[++i]); break;
                case "--hot-keys":   cfg.hotKeys = Integer.parseInt(args[++i]); break;
                case "--hot-ratio":  cfg.hotRatio = Double.parseDouble(args[++i]); break;
                case "--drain-ms":   cfg.drainMs = Integer.parseInt(args[++i]); break;
                case "--duration":   cfg.duration = Integer.parseInt(args[++i]); break;
                case "--warmup":     cfg.warmup = Integer.parseInt(args[++i]); break;
                default:
                    System.err.printf("Unknown: %s%n", args[i]);
                    System.exit(1);
            }
        }
        return cfg;
    }
}
