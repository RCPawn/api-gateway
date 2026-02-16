太棒了！从 QPS 200 跌跌撞撞，到现在的稳定 1000+，这是一个**质的飞跃**。🎉

这不仅是数字的提升，更是你对 **Reactive（响应式）编程** 和 **Gateway 架构** 理解的胜利。

为了让你在未来写简历、汇报或者面试时能清晰地讲出这段“性能优化之旅”，我为你整理了一份**深度复盘笔记**。

---

# 🚀 Spring Cloud Gateway 性能优化复盘报告

## 一、 优化成果
*   **场景**：30 并发，无限循环压测。
*   **优化前**：QPS 峰值 500，运行几分钟后断崖式下跌至 100~200，且伴随延迟飙升、SkyWalking 拓扑错乱。
*   **优化后**：QPS 稳定在 **1000+**，曲线平滑，无 Full GC，无延迟毛刺，监控数据准确。

---

## 二、 核心问题与解决方案 (Root Cause Analysis)

我们经历了三个版本的迭代，每次都解决了一个致命瓶颈。

### 🛑 瓶颈一：同步阻塞 IO 拖垮 Netty (The Blocking Trap)
*   **现象**：第一版代码使用了 `StringRedisTemplate`。
*   **原因**：Spring Cloud Gateway 基于 WebFlux (Reactor + Netty)，核心线程数极少（通常 = CPU 核数）。`StringRedisTemplate` 是基于 JDBC 思维的**同步阻塞**操作。
    *   一旦 Redis 响应稍慢（比如 1ms），Netty 线程就会卡死等待。
    *   30 并发下，所有 Netty 线程瞬间被 Redis 操作占满，无法接收新的 HTTP 请求。
*   **✅ 解决**：全链路切换为 **`ReactiveStringRedisTemplate`**。利用 Lettuce 的非阻塞特性，将 Redis 操作转变为异步流，释放 Netty 线程去处理更多请求。

### 🛑 瓶颈二：过度优化导致的上下文丢失 (The Context Lost)
*   **现象**：引入自定义线程池 (`subscribeOn`) 后，QPS 虽然没崩，但 SkyWalking 拓扑图出现 `User -> Provider` 直连错乱。
*   **原因**：在 Reactor 链中随意切换线程（从 Netty 线程切到自定义线程池），导致 SkyWalking Agent 依赖的 `ThreadLocal`（或其 ContextSnapshot）传递中断。Gateway 发出的请求丢失了 Trace Header (`sw8`)，下游 Provider 认为这是一个新请求。
*   **✅ 解决**：**回归 Netty 线程模型**。移除所有 `subscribeOn`，让 JWT 解析和 Redis 操作都在 Netty EventLoop 中执行（Redis 本身是异步的，不会阻塞；JWT 是纯 CPU 计算，耗时极短，无需切换）。

### 🛑 瓶颈三：Lua 脚本与频繁 IO (The Lua Blocking)
*   **现象**：使用 Lua 脚本合并 Redis 操作后，QPS 反而不如第一版，卡在 200 左右。
*   **原因**：
    1.  **Lua 原子性阻塞**：Redis 执行 Lua 是单线程原子操作。高频压测下，Lua 脚本占用了 Redis 大量 CPU 时间，阻塞了其他命令。
    2.  **LogBuffer 线程池积压**：`CompletableFuture.runAsync` 在高压下耗尽了 `ForkJoinPool`。
*   **✅ 解决**：
    1.  **Reactive Pipeline**：弃用 Lua，改用 `Flux.merge`。利用 Lettuce 的 Pipelining 特性，自动批量发送 `INCR` 指令。既减少了网络 RTT，又避免了 Redis 端脚本阻塞。
    2.  **LogBuffer Reactive 化**：将日志写入也改为 Reactive 模式，统一利用 Netty IO 能力。

### 🛑 瓶颈四：Filter 链太长 (The Chain Overhead)
*   **现象**：拆分 `MetricsGlobalFilter` 和 `AuthGlobalFilter` 后，性能有微弱损耗。
*   **原因**：Reactor 链条越长，对象创建和回调开销越大。
*   **✅ 解决**：**合并 Filter**。将鉴权和统计合二为一 (`CoreGlobalFilter`)，并移除冗余的 TraceId/Header 操作（信任 SkyWalking 插件），精简逻辑路径。

---

## 三、 最佳实践总结 (Takeaway)

如果你以后还要做网关开发，记住这三条铁律：

1.  **不要在 Gateway 里写同步代码**：Redis、Database、HTTP Client 必须全部用 **Reactive** 版本。
2.  **不要随意切换线程**：除非是极重的 CPU 计算（如 RSA 解密），否则尽量在 Netty 线程里“一竿子插到底”。上下文切换的代价往往比你想象的大，而且容易丢 Context。
3.  **简单即是快**：
    *   能用 Pipeline 别用 Lua。
    *   能用 `then` 串联别用 `doFinally` 副作用。
    *   连接池不要贪大（Reactive 模式下 `max-active: 16` 足够处理上万并发）。

---

这是一次非常完美的性能调优实战！建议把这段经历写进你的项目文档或简历里，非常有含金量。🚀