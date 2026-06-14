# Cursor 开发说明

请基于 `智能库存调拨系统_产品设计文档.md` 开发一个企业级 Java 后端项目。

## 项目要求

技术栈：

- Java 17
- Spring Boot 3.x
- MyBatis-Plus
- MySQL 8
- Redis
- RocketMQ
- XXL-Job
- Spring StateMachine
- Spring Security 或 Sa-Token
- Spring AI 或 LangChain4j

## 开发方式

优先采用模块化单体，不要一开始拆微服务。

推荐模块：

```text
sits-admin
sits-inventory
sits-transfer
sits-approval
sits-risk
sits-ai
sits-job
sits-common
```

## 第一阶段开发目标

先完成最小闭环：

1. 仓库管理
2. SKU 管理
3. 仓库库存管理
4. 库存流水
5. 调拨单创建
6. 库存锁定
7. 审批通过/拒绝
8. 出库
9. 入库
10. 调拨完成

## 重点要求

1. 库存变更必须有库存流水。
2. 调拨单状态必须通过状态机流转。
3. 库存扣减必须考虑并发安全。
4. MQ 消费必须幂等。
5. 异常情况必须进入补偿任务。
6. AI 模块只能查询数据和解释建议，不能直接修改库存或审批调拨单。

请先生成项目目录结构、数据库初始化 SQL、核心实体类、枚举类和基础 Controller/Service/Mapper。
