# SITS — Smart Inventory Transfer System

智能库存调拨系统 — 基于 Spring Boot 3.x 的模块化单体应用，实现多仓库库存管理、智能风险识别、自动调拨建议与全链路调拨执行。

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| ORM | MyBatis-Plus 3.5.6 |
| Database | MySQL 8.0 |
| Cache / Lock | Redis + Redisson |
| State Machine | Spring StateMachine 4.0.0 |
| Message Queue | RocketMQ |
| Scheduled Tasks | XXL-Job 2.4.1 |
| Auth | Sa-Token 1.38.0 (RBAC) |
| AI | Spring AI 1.0.0-M4 |
| API Docs | Knife4j (OpenAPI 3) |

## Module Structure

```
sits-parent
├── sits-common       — 公共模块：枚举、异常、基础类、工具类、Sa-Token配置
├── sits-admin        — 管理模块：仓库CRUD、SKU CRUD
├── sits-inventory    — 库存模块：库存查询、锁定/解锁/出库/入库（乐观锁并发安全）
├── sits-transfer     — 调拨模块：调拨单全生命周期 + Spring StateMachine
├── sits-approval     — 审批模块：审批记录
├── sits-risk         — 风险模块：风险扫描、调拨建议生成、补偿任务、MQ幂等
├── sits-ai           — AI模块：Spring AI Copilot（只读工具）
├── sits-job          — 任务模块：XXL-Job定时任务处理器
└── sits-bootstrap    — 启动模块：入口类 + 配置文件 + 数据库迁移脚本
```

## Quick Start

### Prerequisites

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- RocketMQ 5.x (optional, for MQ features)
- XXL-Job Admin (optional, for scheduled tasks)

### Setup

1. **Clone & build**
   ```bash
   mvn clean install -DskipTests
   ```

2. **Create database**
   ```sql
   CREATE DATABASE sits_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
   The schema is auto-created by Flyway on first startup (`V1__init_schema.sql`).

3. **Configure**
   Edit `sits-bootstrap/src/main/resources/application-dev.yml`:
   - MySQL connection
   - Redis connection
   - RocketMQ name server (if using MQ)
   - XXL-Job admin addresses (if using scheduled tasks)

4. **Run**
   ```bash
   cd sits-bootstrap
   mvn spring-boot:run
   ```
   Or run [`SitsApplication.java`](sits-bootstrap/src/main/java/com/sits/SitsApplication.java) directly in your IDE.

5. **Access API Docs**
   Open http://localhost:8080/swagger-ui.html

## Core Business Flow

```
Warehouse + SKU (CRUD)
       ↓
Inventory (stock tracking per warehouse)
       ↓
Risk Scan (XXL-Job, every 30min)
       ↓
Transfer Suggestion (auto-generated with scoring)
       ↓
Transfer Order (full lifecycle with StateMachine)
  CREATED → STOCK_LOCKED → APPROVING → APPROVED → OUTBOUNDING
  → OUTBOUNDED → IN_TRANSIT → INBOUNDING → COMPLETED
       ↓
Approval Records (logged per transfer)
```

## API Summary

| Module | Base Path | Key Operations |
|--------|----------|---------------|
| Auth | `/api/auth` | login, logout, check |
| Warehouses | `/api/warehouses` | CRUD |
| SKUs | `/api/skus` | CRUD |
| Inventory | `/api/inventories` | query, flows |
| Transfer Orders | `/api/transfer-orders` | create, lock-stock, approve/reject, outbound, inbound, cancel |
| Approvals | `/api/approvals` | query, record |
| Risks | `/api/risks` | scan, list, generate suggestions, compensation tasks |
| AI Copilot | `/api/ai` | chat |

## Key Design Decisions

### Concurrent Safety
All inventory operations use MyBatis-Plus `@Version` optimistic locking. Stock flow records are written BEFORE stock updates (idempotent via unique constraint on `biz_type + biz_no + change_type`).

### State Machine
Transfer order lifecycle is driven by Spring StateMachine (12 states, 11 events). No if-else spaghetti — each transition is validated by the SM before execution.

### Idempotency
- **Inventory flows**: unique on `(biz_type, biz_no, change_type)`
- **MQ consumption**: unique on `(event_id, consumer_group)`
- **Compensation tasks**: exponential backoff retry (1→2→4→8→16 min), then manual

### AI Read-Only
The AI Copilot uses Spring AI function calling with read-only tools: it can query inventory, risks, suggestions, and transfer orders, but cannot modify anything.

## Roles (RBAC)

| Role | Permissions |
|------|------------|
| ADMIN | Full access |
| OPERATOR | View inventory, transfer orders, risks |
| WAREHOUSE | Execute outbound/inbound |
| SUPERVISOR | Approve/reject transfers |

## Database Tables (12)

1. `warehouse` — 仓库
2. `sku` — 商品
3. `warehouse_inventory` — 仓库库存（含乐观锁version）
4. `inventory_flow` — 库存流水
5. `sales_stat_daily` — 销量日统计
6. `inventory_risk` — 库存风险
7. `transfer_suggestion` — 调拨建议
8. `transfer_order` — 调拨单（含乐观锁version）
9. `transfer_order_log` — 调拨单日志
10. `approval_record` — 审批记录
11. `compensation_task` — 补偿任务
12. `mq_consume_record` — MQ消费记录（幂等）
