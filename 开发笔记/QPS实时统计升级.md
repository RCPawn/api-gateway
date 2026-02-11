看到 Redis 中出现 `gateway:metrics:qps:total:23:21:22` 这种格式，结合你的代码，我们可以从以下几个维度拆解问题：

### 1. 那些数字（21, 22, 23）是什么？

这其实是你的 **时间戳格式化** 结果。
在你的代码中，格式定义为：
`DateTimeFormatter.ofPattern("HH:mm:ss")`

* **23**: 代表 **小时**（23点）。
* **21**: 代表 **分钟**。
* **22**: 代表 **秒**。

所以，`gateway:metrics:qps:total:23:21:22` 这一整个字符串就是一个 **Key**，它代表了“在 23点21分22秒 这一秒钟内进入网关的请求总数”。

---

### 2. 为什么迅速点击有数据，过一会就变 0？

这涉及到你代码中的 **过期策略** 和 **统计逻辑** 的冲突：

#### A. 过期删除（数据消失）

你在 Filter 中设置了：
`redisTemplate.expire(key, 60, TimeUnit.SECONDS);`
这意味着每一秒产生的 Key 只能活 **60 秒**。当你停止点击 1 分钟后，Redis 会自动删掉这些 Key。你在 Redis 管理工具里就再也看不到了。

#### B. 统计窗口滑出（显示变 0）

在 `DashboardController` 中，你的逻辑是：

1. 获取**当前这一秒**以及**过去 4 秒**（共 5 秒）的数据。
2. `totalRequests / 5` 计算平均值。

**现象解释：**

* **迅速点击时**：你产生的请求落在了“当前秒”或“前几秒”的 Key 里，Controller 能查到数据，计算出 QPS。
* **停止点击后**：
* **5秒内**：虽然 Key 还在 Redis 里，但 Controller 查的是“现在的 5 秒”，而你的数据留在“5秒前”的 Key 里了。此时 `totalRequests` 迅速变小，QPS 降低。
* **5秒后**：Controller 循环查询的 5 个 Key 在 Redis 中虽然可能还没过期，但里面没有数值（或者 Key 根本没生成），所以结果变为 0。



---

### 3. 如何修复，让数据更平滑？

如果你希望在大屏上看到更持久、更稳定的数据，建议做以下改进：

#### 修改方案：使用“滑动窗口”思想

不要只查 5 秒，或者将过期的 Key 存留时间拉长，并确保 Controller 能覆盖到产生数据的区间。

**优化建议代码（Controller 部分）：**

```java
// 如果想让 QPS 看起来不那么“极端”，可以统计过去 10 秒或 30 秒的平均值
for (int i = 0; i < 10; i++) { 
    String second = now.minusSeconds(i).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    String key = KEY_QPS_TOTAL + ":" + second;
    String count = redisTemplate.opsForValue().get(key);
    if (count != null) {
        totalRequests += Integer.parseInt(count);
    }
}
int qps = totalRequests / 10; 

```

#### 进阶方案：使用 Redis 的 HyperLogLog 或 Lua 脚本

目前的方案每秒产生一个 Key，对 Redis 其实是一种浪费。

* **生产环境建议**：使用 `REDIS_KEY:yyyyMMddHH` 作为大 Key，里面存储 `Hash` 结构，`field` 为秒，`value` 为计数。
* 这样你只需要设置一个按小时过期的策略即可，查询也更方便。

### 总结排查建议

1. **确认时间同步**：确保你的网关服务器和 Dashboard 服务器时间一致，否则 Controller 查的是“未来”或“过去”的秒，永远对不上。
2. **查看 TTL**：在 Redis 中用 `TTL gateway:metrics:qps:total:23:21:22` 命令看看，确认它是否正在倒计时消失。

**需要我帮你把代码重构成更稳定的 Hash 结构存储方式吗？**