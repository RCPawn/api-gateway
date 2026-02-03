
# 🛡️ Microservice Gateway Platform

> **微服务流量治理与统一接入平台**
>
> 基于 Spring Cloud Alibaba 生态构建的企业级微服务网关，深度整合了 **动态路由热更新**、**全链路身份安全闭环**、**Redis 防重放** 及 **可视化流量治理**。

---

## 🏗️ 系统架构图

```mermaid
graph TD
%% =======================
%% 🎨 样式定义 (高对比度配色)
%% =======================
%% 蓝色系：客户端 - 黑字
    classDef client fill:#e3f2fd,stroke:#1565c0,stroke-width:2px,color:#000000;
%% 绿色系：网关 - 黑字
    classDef gateway fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px,color:#000000;
%% 黄色系：微服务 - 黑字
    classDef service fill:#fff9c4,stroke:#fbc02d,stroke-width:2px,color:#000000;
%% 紫色系：基础设施 - 黑字
    classDef infra fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px,color:#000000;

%% =======================
%% 1. 入口层
%% =======================
    Client([💻 Client / Browser]):::client
    Admin([🛠️ Vue3 Dashboard]):::client

%% =======================
%% 2. 网关核心层 (Gateway)
%% =======================
subgraph Gateway_Core ["API Gateway Core"]
    direction TB
    G_Auth["🛡️ JWT Auth Filter"]:::gateway
    G_Sec["🔒 Replay Attack Filter"]:::gateway
    G_Limit["🚦 Sentinel Limiter"]:::gateway
    G_Route["🔀 Dynamic Routing"]:::gateway
    
    %% 流程：鉴权 -> 防重放 -> 限流 -> 路由
    G_Auth --> G_Sec --> G_Limit --> G_Route
end

%% =======================
%% 3. 微服务层 (Service Mesh)
%% =======================
subgraph Services ["Microservice Cluster"]
    direction LR
    Consumer["🛒 Consumer Service"]:::service
    Provider["📦 Provider Service"]:::service
    
    %% 内部RPC
    Consumer --"Feign (Token Relay)"--> Provider
end

%% =======================
%% 4. 基础设施层 (Infra)
%% =======================
subgraph Infrastructure ["Infrastructure"]
    Nacos[("Nacos Config/Registry")]:::infra
    Sentinel[("Sentinel Dashboard")]:::infra
    Redis[("Redis Cache")]:::infra
end

%% =======================
%% 5. 连线关系
%% =======================
    Client --> G_Auth
    Admin --> G_Route

    %% 网关向下分发
    G_Route --> Consumer
    G_Route --> Provider

    %% 基础设施交互
    G_Sec -.->|Check Nonce| Redis
    G_Limit -.->|Push Rules| Sentinel
    G_Route -.->|Pull Routes| Nacos
    Services -.->|Register| Nacos
```

---

## 🌟 核心特性与进度

### 🚀 Backend Core (后端核心)
*   ✅ **基础架构搭建**：完成 Nacos 注册中心接入，打通 Gateway -> Consumer -> Provider 调用链路。
*   ✅ **全链路身份闭环**：设计 `ThreadLocal` + `Feign` 拦截器透传方案，实现 Token/UserID 在微服务链中的无缝传递。
*   ✅ **动态路由热更新**：基于 Nacos Config 监听机制，实现路由配置修改**秒级生效**，无需重启网关。
*   ✅ **流量治理**：
    *   集成 Sentinel 实现网关层限流与熔断降级。
    *   **自定义异常处理**：返回标准化的 JSON 提示。
    *   配置规则持久化到 Nacos，避免重启丢失。
*   ✅ **安全防御体系**：
    *   **防重放攻击 (Replay Attack)**：基于 `Redis` + `Nonce` + `Timestamp` 机制，有效拦截恶意重复请求。
    *   **全局 CORS**：统一解决前后端分离跨域问题。
*   ✅ **API 文档聚合**：集成 Knife4j，统一聚合所有微服务的 Swagger 文档。

### 💻 可视化控制台
*   ✅ **管理后台**：基于 Vue 3 + Vite + Element Plus 构建。
*   ✅ **路由可视化管理**：彻底告别手写 JSON！实现路由的**在线新增、编辑、删除**，操作结果实时同步至 Nacos。
*   ⬜ **流量驾驶舱**：(TODO) 接入 ECharts 展示实时 QPS、CPU 水位监控。

![image-20260120222844020](README.assets/image-20260120222844020.png)

---

## 🔍 核心技术原理解析

### 1. 全链路 Token 透传机制
> **技术原理**：采用“手提箱”模式。在网关层将 UserID 装入 HTTP Header，在服务内部存入 ThreadLocal，在发起 Feign 调用前再次拦截并注入 Header。

```mermaid
sequenceDiagram
    autonumber
    participant Client as 客户端
    participant Gateway as 网关(Gateway)
    participant Consumer as 消费者(Consumer)
    participant Provider as 提供者(Provider)
Note over Client, Gateway: 阶段1：身份注入
Client->>Gateway: 1. 请求携带 Token

rect rgb(230, 240, 255)
Note over Gateway: 网关层处理
Gateway->>Gateway: AuthGlobalFilter: 校验 Token
Gateway->>Gateway: 解析 UserID -> 注入 Header
end

Gateway->>Consumer: 2. 转发请求 (携带 Header)

rect rgb(255, 250, 230)
Note over Consumer: 消费者服务
Consumer->>Consumer: MVC Interceptor: Header -> ThreadLocal
Consumer->>Consumer: 业务逻辑 (UserContext)
Consumer->>Consumer: Feign Interceptor: ThreadLocal -> Header
end

Consumer->>Provider: 3. Feign RPC 调用 (携带 Header)

rect rgb(230, 255, 230)
Note over Provider: 提供者服务
Provider->>Provider: MVC Interceptor: Header -> ThreadLocal
Provider->>Provider: 业务闭环
end
```

### 2. 防重放攻击防御机制 (Replay Attack Prevention)
> **技术原理**：利用 **Redis (Nonce) + Timestamp (时间戳)** 双重校验机制。防止黑客截获合法请求后进行恶意重放。

```mermaid
flowchart TD
    %% 修复语法：给所有文本加上双引号，确保特殊字符不报错
    Start(["收到请求 Header: <br/>Nonce + Timestamp"]) --> CheckTime{"1. 时间戳校验<br/>(Timestamp)"}
    
    CheckTime -- "超时 (>5min)" --> Reject1["⛔ 拒绝: 请求已过期"]
    CheckTime -- "有效 (<=5min)" --> CheckRedis{"2. Redis 查重<br/>(exists Nonce)"}
    
    CheckRedis -- "已存在 (重复请求)" --> Reject2["⛔ 拒绝: 检测到重放攻击"]
    CheckRedis -- "不存在 (新请求)" --> SaveRedis["✅ 存入 Redis<br/>(Key=Nonce, TTL=5min)"]
    
    SaveRedis --> Pass(["🚀 放行请求"])
    
    style Reject1 fill:#ffcdd2,stroke:#b71c1c,color:#000
    style Reject2 fill:#ffcdd2,stroke:#b71c1c,color:#000
    style Pass fill:#c8e6c9,stroke:#2e7d32,color:#000
```

### 3. 统一接口文档聚合 (Knife4j Aggregation)
> **技术原理**：网关作为流量入口，统一拦截 Swagger 资源请求，并根据路由规则重写路径，将下游微服务的文档数据聚合展示。

```mermaid
sequenceDiagram
    autonumber
    actor User as 开发者
    participant Gateway as 网关 (Port:9000)
    participant Consumer as 微服务 (Port:8081)

    Note over User, Gateway: 步骤1：加载框架
    User->>Gateway: 访问 /doc.html
    Gateway-->>User: 返回 Knife4j 页面

    Note over User, Gateway: 步骤2：获取分组
    User->>Gateway: 请求 /v3/api-docs/swagger-config
    Gateway-->>User: 返回聚合配置 (服务列表)

    Note over User, Consumer: 步骤3：拉取文档
    User->>Gateway: 请求 /service-consumer/v3/api-docs
    Gateway->>Gateway: StripPrefix: 去除前缀
    Gateway->>Consumer: 转发请求
    Consumer-->>Gateway: 返回 OpenApi JSON
    Gateway-->>User: 渲染接口文档
```

### 4. 网关异步日志与审计
```mermaid
graph TD
%% 样式定义
    classDef client fill:#e3f2fd,stroke:#1565c0,stroke-width:2px;
    classDef filter fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px,rx:5;
    classDef mq fill:#ff9800,stroke:#e65100,color:white,rx:5;
    classDef db fill:#2196f3,stroke:#0d47a1,color:white,rx:5;
    classDef service fill:#fff9c4,stroke:#fbc02d,color:black,rx:5;

    Client([👤 Client]) --> Gateway

subgraph Gateway [API Gateway]
%% 核心逻辑：Filter 只是一个切面
LogFilter[📝 LogGlobalFilter]:::filter
Routing((Netty Routing)):::filter

LogFilter --> Routing
end

%% 主业务流 (Main Flow)
Routing <==>|HTTP Request/Response| MicroService[📦 MicroServices]:::service

%% 异步旁路 (Async Sidecar)
LogFilter --"🔥 Fire & Forget (Log DTO)"--> MQ

subgraph Async_Audit [Async Audit System]
MQ((RabbitMQ)):::mq
LogService[⚙️ Service-Log]
DB[(MySQL)]:::db

MQ -->|Consume| LogService
LogService -->|Insert| DB
end
```
---

## 🛠️ 技术栈
*   **Core Framework**: Spring Boot 3.x, Spring Cloud Alibaba 2022.x
*   **Gateway**: Spring Cloud Gateway (WebFlux 响应式编程)
*   **Middleware**: Nacos 2.x, Sentinel, Redis
*   **RPC**: OpenFeign
*   **Frontend**: Vue 3, Vite, Element Plus, ECharts
