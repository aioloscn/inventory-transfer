package com.sits.risk.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sits.admin.entity.Warehouse;
import com.sits.admin.mapper.SkuMapper;
import com.sits.admin.mapper.WarehouseMapper;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.enums.RiskLevel;
import com.sits.common.enums.RiskStatus;
import com.sits.common.enums.RiskType;
import com.sits.common.enums.SuggestionStatus;
import com.sits.common.exception.BusinessException;
import com.sits.common.util.NoGenerator;
import com.sits.inventory.entity.SalesStatDaily;
import com.sits.inventory.entity.WarehouseInventory;
import com.sits.inventory.mapper.SalesStatDailyMapper;
import com.sits.inventory.mapper.WarehouseInventoryMapper;
import com.sits.risk.dto.GenerateSuggestionsRequest;
import com.sits.risk.dto.RiskScanResult;
import com.sits.risk.dto.SuggestionExplainContext;
import com.sits.risk.entity.CompensationTask;
import com.sits.risk.entity.InventoryRisk;
import com.sits.risk.entity.MqConsumeRecord;
import com.sits.risk.mapper.CompensationTaskMapper;
import com.sits.risk.mapper.InventoryRiskMapper;
import com.sits.risk.mapper.MqConsumeRecordMapper;
import com.sits.risk.service.DistributedLockService;
import com.sits.risk.service.RiskService;
import com.sits.risk.service.RuleConfigService;
import com.sits.risk.service.TransferSuggestionExplanationEnhancer;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.entity.TransferSuggestion;
import com.sits.transfer.mapper.TransferSuggestionMapper;
import com.sits.transfer.service.TransferOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RiskService 实现类。
 *
 * <p>核心算法：
 * <ul>
 *   <li><b>风险扫描：</b>游标分页遍历 SKU 表，批量查询库存和销量，内存中计算风险，批量 upsert。</li>
 *   <li><b>建议生成：</b>对每个未解决的缺货风险，找到最优的源仓库，生成带评分的调拨建议。</li>
 * </ul>
 */
@Service
public class RiskServiceImpl implements RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskServiceImpl.class);

    /** 分布式锁 key */
    private static final String LOCK_KEY = "sku:lock";
    /** 扫描批次号前缀 */
    private static final String SCAN_BATCH_PREFIX = "RISK_SCAN_";

    /** 阈值键名常量 */
    private static final String KEY_SHORTAGE_HIGH = "shortage_high_days";
    private static final String KEY_SHORTAGE_MEDIUM = "shortage_medium_days";
    private static final String KEY_OVERSTOCK_DAYS = "overstock_days";
    private static final String KEY_OVERSTOCK_RATIO = "overstock_ratio";
    private static final String KEY_LOOKBACK_DAYS = "avg_sales_lookback_days";
    private static final String KEY_SUGGESTION_EXPIRE = "suggestion_expire_days";
    private static final String KEY_SCAN_PAGE_SIZE = "risk_scan_page_size";
    private static final String KEY_LOCK_LEASE_MINUTES = "risk_scan_lock_lease_minutes";

    private final InventoryRiskMapper riskMapper;
    private final WarehouseInventoryMapper inventoryMapper;
    private final SalesStatDailyMapper salesStatMapper;
    private final TransferSuggestionMapper suggestionMapper;
    private final CompensationTaskMapper compensationTaskMapper;
    private final MqConsumeRecordMapper mqConsumeRecordMapper;
    private final WarehouseMapper warehouseMapper;
    private final TransferOrderService transferOrderService;
    private final RuleConfigService ruleConfigService;
    private final SkuMapper skuMapper;
    private final DistributedLockService lockService;
    private final TransferSuggestionExplanationEnhancer enhancer;

    @Autowired
    @Lazy
    private RiskService self;

    public RiskServiceImpl(InventoryRiskMapper riskMapper,
                           WarehouseInventoryMapper inventoryMapper,
                           SalesStatDailyMapper salesStatMapper,
                           TransferSuggestionMapper suggestionMapper,
                           CompensationTaskMapper compensationTaskMapper,
                           MqConsumeRecordMapper mqConsumeRecordMapper,
                           WarehouseMapper warehouseMapper,
                           TransferOrderService transferOrderService,
                           RuleConfigService ruleConfigService,
                           SkuMapper skuMapper,
                           DistributedLockService lockService,
                           @Lazy TransferSuggestionExplanationEnhancer enhancer) {
        this.riskMapper = riskMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesStatMapper = salesStatMapper;
        this.suggestionMapper = suggestionMapper;
        this.compensationTaskMapper = compensationTaskMapper;
        this.mqConsumeRecordMapper = mqConsumeRecordMapper;
        this.warehouseMapper = warehouseMapper;
        this.transferOrderService = transferOrderService;
        this.ruleConfigService = ruleConfigService;
        this.skuMapper = skuMapper;
        this.lockService = lockService;
        this.enhancer = enhancer;
    }

    // ==================== 风险扫描 ====================

    @Override
    public RiskScanResult scanRisks() {
        // 1. 获取分布式锁
        int lockLeaseMinutes = ruleConfigService.getIntValue(KEY_LOCK_LEASE_MINUTES, 30);
        if (!lockService.tryLock(LOCK_KEY, 0, lockLeaseMinutes, TimeUnit.MINUTES)) {
            return RiskScanResult.locked();
        }

        String scanBatchNo = SCAN_BATCH_PREFIX + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int pageSize = ruleConfigService.getIntValue(KEY_SCAN_PAGE_SIZE, 1000);
        int lookbackDays = ruleConfigService.getIntValue(KEY_LOOKBACK_DAYS, 30);
        LocalDateTime startTime = LocalDateTime.now();
        long totalSkuCount = 0;
        long totalRiskCount = 0;
        long lastSkuId = 0;

        try {
            log.info("Risk scan start: scanBatchNo={}, pageSize={}, lookbackDays={}", scanBatchNo, pageSize, lookbackDays);

            while (true) {
                List<Long> skuIds = skuMapper.selectSkuIdsByCursor(lastSkuId, pageSize);
                if (skuIds.isEmpty()) break;

                long batchStart = System.currentTimeMillis();
                int batchRiskCount = self.processBatch(skuIds, scanBatchNo, lookbackDays);

                totalSkuCount += skuIds.size();
                totalRiskCount += batchRiskCount;
                lastSkuId = skuIds.get(skuIds.size() - 1);

                long batchCost = System.currentTimeMillis() - batchStart;
                log.info("Batch done: lastSkuId={}, batchSize={}, riskCount={}, cost={}ms",
                        lastSkuId, skuIds.size(), batchRiskCount, batchCost);

                if (skuIds.size() < pageSize) break;
            }

            // 自动关闭本轮未命中的 NEW 风险
            int resolvedCount = riskMapper.closeResolvedRisks(scanBatchNo);
            log.info("Closed {} resolved risks for scanBatchNo={}", resolvedCount, scanBatchNo);

            LocalDateTime endTime = LocalDateTime.now();
            long costMillis = Duration.between(startTime, endTime).toMillis();
            log.info("Risk scan done: scanBatchNo={}, totalSku={}, risks={}, resolved={}, cost={}ms",
                    scanBatchNo, totalSkuCount, totalRiskCount, resolvedCount, costMillis);

            return RiskScanResult.success(scanBatchNo, totalSkuCount, totalRiskCount, resolvedCount, startTime, endTime);
        } catch (Exception e) {
            log.error("Risk scan failed: scanBatchNo={}", scanBatchNo, e);
            return RiskScanResult.fail(scanBatchNo, e.getMessage());
        } finally {
            lockService.unlock(LOCK_KEY);
        }
    }

    /**
     * 处理一批 SKU：批量查库存 + 销量 → 内存计算风险 → 批量 upsert。
     * 每批独立事务提交。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processBatch(List<Long> skuIds, String scanBatchNo, int lookbackDays) {
        // 1. 批量查询库存
        List<WarehouseInventory> inventories = inventoryMapper.selectBySkuIds(skuIds);
        if (inventories.isEmpty()) return 0;

        // 2. 批量查询销量聚合
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(lookbackDays - 1);
        List<Map<String, Object>> salesRows = salesStatMapper.selectAggregatedBySkuIds(skuIds, startDate, endDate);

        // 销量聚合：key = "skuId_warehouseId", value = totalSales
        Map<String, Integer> salesMap = new HashMap<>();
        for (Map<String, Object> row : salesRows) {
            Long sSkuId = ((Number) row.get("sku_id")).longValue();
            Long sWhId = ((Number) row.get("warehouse_id")).longValue();
            Integer totalSales = ((Number) row.get("total_sales")).intValue();
            salesMap.put(sSkuId + "_" + sWhId, totalSales);
        }

        // 3. 读取阈值配置
        int shortageHighDays = ruleConfigService.getIntValue(KEY_SHORTAGE_HIGH, 3);
        int shortageMediumDays = ruleConfigService.getIntValue(KEY_SHORTAGE_MEDIUM, 7);
        BigDecimal overstockRatio = new BigDecimal(ruleConfigService.getValue(KEY_OVERSTOCK_RATIO, "0.8"));
        int overstockDays = ruleConfigService.getIntValue(KEY_OVERSTOCK_DAYS, 30);

        // 4. 内存中计算风险
        List<InventoryRisk> risks = new ArrayList<>();
        for (WarehouseInventory inv : inventories) {
            if (inv.getSafetyStock() == null || inv.getSafetyStock() == 0) continue;

            int available = inv.getAvailableStock() != null ? inv.getAvailableStock() : 0;
            int safety = inv.getSafetyStock();
            Integer daysWithSales = salesMap.getOrDefault(inv.getSkuId() + "_" + inv.getWarehouseId(), 0);
            BigDecimal avgDailySales = BigDecimal.valueOf(daysWithSales)
                    .divide(BigDecimal.valueOf(lookbackDays), 2, RoundingMode.HALF_UP);

            // --- 缺货风险 ---
            if (available < safety) {
                if (avgDailySales.compareTo(BigDecimal.ZERO) <= 0) {
                    // 无销售数据时库存不会消耗，标记为低风险
                    InventoryRisk risk = new InventoryRisk();
                    risk.setRiskNo(NoGenerator.generateRiskNo());
                    risk.setSkuId(inv.getSkuId());
                    risk.setWarehouseId(inv.getWarehouseId());
                    risk.setRiskType(RiskType.SHORTAGE.name());
                    risk.setRiskLevel(RiskLevel.LOW.name());
                    risk.setAvailableStock(available);
                    risk.setAvgDailySales(BigDecimal.ZERO);
                    risk.setSupportDays(BigDecimal.ZERO);
                    risk.setScanBatchNo(scanBatchNo);
                    risk.setRiskFingerprint(InventoryRisk.fingerprint(inv.getSkuId(), inv.getWarehouseId(), RiskType.SHORTAGE.name()));
                    risks.add(risk);
                    continue;
                }

                BigDecimal supportDays = BigDecimal.valueOf(available)
                        .divide(avgDailySales, 2, RoundingMode.HALF_UP);

                RiskLevel level;
                if (supportDays.compareTo(BigDecimal.valueOf(shortageHighDays)) < 0) {
                    level = RiskLevel.HIGH;
                } else if (supportDays.compareTo(BigDecimal.valueOf(shortageMediumDays)) < 0) {
                    level = RiskLevel.MEDIUM;
                } else {
                    level = RiskLevel.LOW;
                }

                InventoryRisk risk = new InventoryRisk();
                risk.setRiskNo(NoGenerator.generateRiskNo());
                risk.setSkuId(inv.getSkuId());
                risk.setWarehouseId(inv.getWarehouseId());
                risk.setRiskType(RiskType.SHORTAGE.name());
                risk.setRiskLevel(level.name());
                risk.setAvailableStock(available);
                risk.setAvgDailySales(avgDailySales);
                risk.setSupportDays(supportDays);
                risk.setScanBatchNo(scanBatchNo);
                risk.setRiskFingerprint(InventoryRisk.fingerprint(inv.getSkuId(), inv.getWarehouseId(), RiskType.SHORTAGE.name()));
                risks.add(risk);
            }

            // --- 积压风险 ---
            if (inv.getMaxStock() != null && inv.getMaxStock() > 0) {
                BigDecimal threshold = BigDecimal.valueOf(inv.getMaxStock()).multiply(overstockRatio);
                if (available > threshold.intValue() && avgDailySales.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal supportDays = BigDecimal.valueOf(available)
                            .divide(avgDailySales, 2, RoundingMode.HALF_UP);
                    if (supportDays.compareTo(BigDecimal.valueOf(overstockDays)) > 0) {
                        InventoryRisk risk = new InventoryRisk();
                        risk.setRiskNo(NoGenerator.generateRiskNo());
                        risk.setSkuId(inv.getSkuId());
                        risk.setWarehouseId(inv.getWarehouseId());
                        risk.setRiskType(RiskType.OVERSTOCK.name());
                        risk.setRiskLevel(RiskLevel.LOW.name());
                        risk.setAvailableStock(available);
                        risk.setAvgDailySales(avgDailySales);
                        risk.setSupportDays(supportDays);
                        risk.setScanBatchNo(scanBatchNo);
                        risk.setRiskFingerprint(InventoryRisk.fingerprint(inv.getSkuId(), inv.getWarehouseId(), RiskType.OVERSTOCK.name()));
                        risks.add(risk);
                    }
                }
            }
        }

        // 5. 批量 upsert
        if (!risks.isEmpty()) {
            riskMapper.batchUpsert(risks);
        }
        return risks.size();
    }

    @Override
    @Transactional
    public void rescanRisk(Long skuId, Long warehouseId) {
        WarehouseInventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<WarehouseInventory>()
                        .eq(WarehouseInventory::getSkuId, skuId)
                        .eq(WarehouseInventory::getWarehouseId, warehouseId)
        );
        if (inv == null) {
            log.info("No inventory found for rescan: skuId={}, warehouseId={}", skuId, warehouseId);
            return;
        }

        // 将该 SKU+仓库 已有的未解决风险标记为已解决
        List<InventoryRisk> existingRisks = riskMapper.selectList(
                new LambdaQueryWrapper<InventoryRisk>()
                        .eq(InventoryRisk::getSkuId, skuId)
                        .eq(InventoryRisk::getWarehouseId, warehouseId)
                        .in(InventoryRisk::getStatus, RiskStatus.NEW.name(), RiskStatus.PROCESSING.name())
        );
        for (InventoryRisk r : existingRisks) {
            r.setStatus(RiskStatus.RESOLVED.name());
            riskMapper.updateById(r);
        }
        if (!existingRisks.isEmpty()) {
            log.info("Resolved {} existing risks for skuId={}, warehouseId={}",
                    existingRisks.size(), skuId, warehouseId);
        }

        // rescan 是对单个 SKU+仓库的即时评估，不需要 scanBatchNo
        evaluateSingleRisk(inv);

        log.info("Risk rescan complete: skuId={}, warehouseId={}", skuId, warehouseId);
    }

    /**
     * 评估单条库存的风险并保存（供 rescanRisk 等局部重扫使用）。
     */
    private void evaluateSingleRisk(WarehouseInventory inv) {
        if (inv.getSafetyStock() == null || inv.getSafetyStock() == 0) return;

        BigDecimal avgDailySales = computeAvgDailySales(inv.getSkuId(), inv.getWarehouseId());
        int available = inv.getAvailableStock() != null ? inv.getAvailableStock() : 0;
        int safety = inv.getSafetyStock();

        // 缺货
        if (available < safety) {
            int shortageHighDays = ruleConfigService.getIntValue(KEY_SHORTAGE_HIGH, 3);
            int shortageMediumDays = ruleConfigService.getIntValue(KEY_SHORTAGE_MEDIUM, 7);

            RiskLevel level;
            BigDecimal supportDays;
            if (avgDailySales.compareTo(BigDecimal.ZERO) <= 0) {
                level = RiskLevel.LOW;
                supportDays = BigDecimal.ZERO;
            } else {
                supportDays = BigDecimal.valueOf(available).divide(avgDailySales, 2, RoundingMode.HALF_UP);
                if (supportDays.compareTo(BigDecimal.valueOf(shortageHighDays)) < 0) level = RiskLevel.HIGH;
                else if (supportDays.compareTo(BigDecimal.valueOf(shortageMediumDays)) < 0) level = RiskLevel.MEDIUM;
                else level = RiskLevel.LOW;
            }

            InventoryRisk risk = new InventoryRisk();
            risk.setRiskNo(NoGenerator.generateRiskNo());
            risk.setSkuId(inv.getSkuId());
            risk.setWarehouseId(inv.getWarehouseId());
            risk.setRiskType(RiskType.SHORTAGE.name());
            risk.setRiskLevel(level.name());
            risk.setAvailableStock(available);
            risk.setAvgDailySales(avgDailySales);
            risk.setSupportDays(supportDays);
            risk.setStatus(RiskStatus.NEW.name());
            risk.setRiskFingerprint(InventoryRisk.fingerprint(inv.getSkuId(), inv.getWarehouseId(), RiskType.SHORTAGE.name()));
            riskMapper.insert(risk);
        }

        // 积压
        if (inv.getMaxStock() != null && inv.getMaxStock() > 0) {
            BigDecimal overstockRatio = new BigDecimal(ruleConfigService.getValue(KEY_OVERSTOCK_RATIO, "0.8"));
            int overstockDays = ruleConfigService.getIntValue(KEY_OVERSTOCK_DAYS, 30);
            BigDecimal threshold = BigDecimal.valueOf(inv.getMaxStock()).multiply(overstockRatio);
            if (available > threshold.intValue() && avgDailySales.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal supportDays = BigDecimal.valueOf(available).divide(avgDailySales, 2, RoundingMode.HALF_UP);
                if (supportDays.compareTo(BigDecimal.valueOf(overstockDays)) > 0) {
                    InventoryRisk risk = new InventoryRisk();
                    risk.setRiskNo(NoGenerator.generateRiskNo());
                    risk.setSkuId(inv.getSkuId());
                    risk.setWarehouseId(inv.getWarehouseId());
                    risk.setRiskType(RiskType.OVERSTOCK.name());
                    risk.setRiskLevel(RiskLevel.LOW.name());
                    risk.setAvailableStock(available);
                    risk.setAvgDailySales(avgDailySales);
                    risk.setSupportDays(supportDays);
                    risk.setStatus(RiskStatus.NEW.name());
                    risk.setRiskFingerprint(InventoryRisk.fingerprint(inv.getSkuId(), inv.getWarehouseId(), RiskType.OVERSTOCK.name()));
                    riskMapper.insert(risk);
                }
            }
        }
    }

    @Override
    public void logEventNotification(String eventType, Long skuId, Long warehouseId, int quantity) {
        log.info("Event notification: type={}, skuId={}, warehouseId={}, qty={}",
                eventType, skuId, warehouseId, quantity);
    }

    @Override
    public PageResult<InventoryRisk> pageRisks(PageQuery pageQuery, Long skuId, Long warehouseId,
                                                String riskType, String riskLevel, String status) {
        LambdaQueryWrapper<InventoryRisk> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) wrapper.eq(InventoryRisk::getSkuId, skuId);
        if (warehouseId != null) wrapper.eq(InventoryRisk::getWarehouseId, warehouseId);
        if (riskType != null) wrapper.eq(InventoryRisk::getRiskType, riskType);
        if (riskLevel != null) wrapper.eq(InventoryRisk::getRiskLevel, riskLevel);
        if (status != null) wrapper.eq(InventoryRisk::getStatus, status);
        wrapper.orderByDesc(InventoryRisk::getCreateTime);
        IPage<InventoryRisk> page = riskMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public InventoryRisk getRiskById(Long riskId) {
        InventoryRisk risk = riskMapper.selectById(riskId);
        if (risk == null) throw new BusinessException("风险记录不存在: " + riskId);
        return risk;
    }

    @Override
    public void updateRiskStatus(Long riskId, String status) {
        InventoryRisk risk = riskMapper.selectById(riskId);
        if (risk == null) throw new BusinessException("风险记录不存在: " + riskId);
        risk.setStatus(status);
        riskMapper.updateById(risk);
    }

    // ==================== 调拨建议 ====================

    /** 不应重复生成建议的状态集合 */
    private static final Set<String> ACTIVE_SUGGESTION_STATUSES = Set.of(
            SuggestionStatus.GENERATED.name(),
            SuggestionStatus.CONFIRMED.name(),
            SuggestionStatus.CONVERTED.name()
    );

    @Override
    @Transactional
    public List<TransferSuggestion> generateSuggestions(GenerateSuggestionsRequest request) {
        if (request == null) {
            request = new GenerateSuggestionsRequest();
        }

        final int maxCount = request.getMaxCountOrDefault();
        final boolean dryRun = request.isDryRun();

        log.info("Generating transfer suggestions: maxCount={}, dryRun={}", maxCount, dryRun);

        // 1. 查询需要生成建议的风险（批量，按排序 + 限制）
        List<InventoryRisk> targetRisks = queryTargetRisks(request, maxCount);
        if (targetRisks.isEmpty()) {
            log.info("No target risks found for suggestion generation.");
            return List.of();
        }

        // 2. 批量加载仓库 Map
        Map<Long, Warehouse> warehouseMap = loadWarehouseMap();

        // 3. 提取 skuId 集合，批量查询库存
        Set<Long> skuIdSet = targetRisks.stream().map(InventoryRisk::getSkuId).collect(Collectors.toSet());
        List<WarehouseInventory> allInventories = inventoryMapper.selectBySkuIds(new ArrayList<>(skuIdSet));
        Map<Long, List<WarehouseInventory>> inventoryBySku = allInventories.stream()
                .collect(Collectors.groupingBy(WarehouseInventory::getSkuId));

        List<TransferSuggestion> suggestions = new ArrayList<>();

        for (InventoryRisk risk : targetRisks) {
            // 幂等检查：已存在活跃状态的建议则跳过
            if (!dryRun && hasActiveSuggestion(risk.getId())) {
                log.debug("Skip risk {} - active suggestion already exists", risk.getRiskNo());
                continue;
            }

            Warehouse targetWarehouse = warehouseMap.get(risk.getWarehouseId());
            if (targetWarehouse == null) continue;

            List<WarehouseInventory> skuInventories = inventoryBySku.getOrDefault(risk.getSkuId(), List.of());

            // 查找目标仓库存
            WarehouseInventory targetInv = skuInventories.stream()
                    .filter(i -> i.getWarehouseId().equals(risk.getWarehouseId()))
                    .findFirst().orElse(null);

            // 计算缺口
            int shortageQty = calculateShortageQuantity(risk, targetInv);
            if (shortageQty <= 0) continue;

            // 构建候选源仓
            List<CandidateSource> candidates = buildSuggestionCandidates(risk, targetWarehouse, skuInventories, warehouseMap, shortageQty);

            // 多源仓补足缺口
            int remaining = shortageQty;
            for (CandidateSource c : candidates) {
                if (remaining <= 0) break;

                int qty = Math.min(remaining, c.transferableStock());
                if (qty <= 0) continue;

                String baseReason = buildBaseReason(risk, targetWarehouse, targetInv, c.warehouse(), c.inv(), c.transferableStock(), qty);

                String finalReason = baseReason;
                SuggestionExplainContext ctx = buildExplainContext(risk, targetWarehouse, targetInv,
                        c.warehouse(), c.inv(), c.transferableStock(), qty, c.score(), baseReason);
                finalReason = enhancer.enhance(ctx);
                if (finalReason == null || finalReason.isBlank()) {
                    finalReason = baseReason;
                }

                TransferSuggestion suggestion = new TransferSuggestion();
                suggestion.setSuggestionNo(NoGenerator.generateSuggestionNo());
                suggestion.setRiskId(risk.getId());
                suggestion.setSkuId(risk.getSkuId());
                suggestion.setSourceWarehouseId(c.inv().getWarehouseId());
                suggestion.setTargetWarehouseId(risk.getWarehouseId());
                suggestion.setSuggestQuantity(qty);
                suggestion.setScore(c.score());
                suggestion.setReason(finalReason);
                suggestion.setStatus(SuggestionStatus.GENERATED.name());
                int expireDays = ruleConfigService.getIntValue(KEY_SUGGESTION_EXPIRE, 7);
                suggestion.setExpireTime(LocalDateTime.now().plusDays(expireDays));

                if (!dryRun) {
                    suggestionMapper.insert(suggestion);
                }

                suggestions.add(suggestion);
                remaining -= qty;
            }

            // 更新风险状态
            if (!dryRun) {
                risk.setStatus(RiskStatus.PROCESSING.name());
                riskMapper.updateById(risk);
            }
        }

        log.info("Generated {} transfer suggestions (dryRun={}).", suggestions.size(), dryRun);
        return suggestions;
    }

    /**
     * 查询需要生成建议的目标风险。
     */
    private List<InventoryRisk> queryTargetRisks(GenerateSuggestionsRequest request, int maxCount) {
        LambdaQueryWrapper<InventoryRisk> wrapper = new LambdaQueryWrapper<InventoryRisk>()
                .eq(InventoryRisk::getRiskType, RiskType.SHORTAGE.name())
                .eq(InventoryRisk::getStatus, RiskStatus.NEW.name());

        if (request.getRiskIds() != null && !request.getRiskIds().isEmpty()) {
            wrapper.in(InventoryRisk::getId, request.getRiskIds());
        }

        // 按风险等级优先级 + supportDays + 创建时间排序
        wrapper.orderByAsc(InventoryRisk::getRiskLevel);
        wrapper.orderByAsc(InventoryRisk::getSupportDays);
        wrapper.orderByDesc(InventoryRisk::getCreateTime);
        wrapper.last("LIMIT " + maxCount);

        return riskMapper.selectList(wrapper);
    }

    /**
     * 检查是否存在活跃状态的调拨建议（幂等防重复）。
     */
    private boolean hasActiveSuggestion(Long riskId) {
        return suggestionMapper.selectCount(
                new LambdaQueryWrapper<TransferSuggestion>()
                        .eq(TransferSuggestion::getRiskId, riskId)
                        .in(TransferSuggestion::getStatus, ACTIVE_SUGGESTION_STATUSES)
        ) > 0;
    }

    /**
     * 计算目标仓缺口数量。
     */
    private int calculateShortageQuantity(InventoryRisk risk, WarehouseInventory targetInventory) {
        int targetSafety = targetInventory != null && targetInventory.getSafetyStock() != null
                ? targetInventory.getSafetyStock() : 0;
        int targetAvailable = targetInventory != null && targetInventory.getAvailableStock() != null
                ? targetInventory.getAvailableStock() : 0;
        int targetInTransit = targetInventory != null && targetInventory.getInTransitStock() != null
                ? targetInventory.getInTransitStock() : 0;

        int targetEffective = targetAvailable + targetInTransit;

        // 按安全库存计算缺口
        int shortageBySafety = targetSafety - targetEffective;

        // 按支撑天数计算缺口
        BigDecimal avgDaily = risk.getAvgDailySales();
        int shortageBySupport = 0;
        if (avgDaily != null && avgDaily.compareTo(BigDecimal.ZERO) > 0) {
            int supportDaysTarget = ruleConfigService.getIntValue(KEY_SHORTAGE_MEDIUM, 7);
            int requiredStock = avgDaily.multiply(BigDecimal.valueOf(supportDaysTarget))
                    .setScale(0, RoundingMode.CEILING).intValue();
            shortageBySupport = requiredStock - targetEffective;
        }

        // 可以结合 riskLevel 调整：HIGH 等级使用 shortage_high_days
        if (RiskLevel.HIGH.name().equals(risk.getRiskLevel()) && avgDaily != null && avgDaily.compareTo(BigDecimal.ZERO) > 0) {
            int highDays = ruleConfigService.getIntValue(KEY_SHORTAGE_HIGH, 3);
            BigDecimal requiredForRecovery = avgDaily.multiply(BigDecimal.valueOf(highDays + 3));
            int shortageByHigh = requiredForRecovery.setScale(0, RoundingMode.CEILING).intValue() - targetEffective;
            return Math.max(0, Math.max(shortageByHigh, Math.max(shortageBySafety, shortageBySupport)));
        }

        return Math.max(0, Math.max(shortageBySafety, shortageBySupport));
    }

    /**
     * 计算源仓可调拨数量。
     * <p>可调拨 = 可用库存 - 安全库存，不减去在途库存。
     */
    private int calculateTransferableStock(WarehouseInventory inv) {
        int available = inv.getAvailableStock() != null ? inv.getAvailableStock() : 0;
        int safety = inv.getSafetyStock() != null ? inv.getSafetyStock() : 0;
        return Math.max(0, available - safety);
    }

    /**
     * 构建候选源仓列表（只筛选 transferableStock > 0 的仓库）。
     */
    private List<CandidateSource> buildSuggestionCandidates(
            InventoryRisk risk,
            Warehouse targetWarehouse,
            List<WarehouseInventory> skuInventories,
            Map<Long, Warehouse> warehouseMap,
            int shortageQty) {

        List<CandidateSource> candidates = new ArrayList<>();
        for (WarehouseInventory inv : skuInventories) {
            if (inv.getWarehouseId().equals(risk.getWarehouseId())) continue;

            Warehouse sourceWh = warehouseMap.get(inv.getWarehouseId());
            if (sourceWh == null) continue;

            int transferable = calculateTransferableStock(inv);
            if (transferable <= 0) continue;

            BigDecimal score = scoreCandidate(risk, inv, sourceWh, targetWarehouse, transferable);
            candidates.add(new CandidateSource(inv, sourceWh, transferable, score));
        }

        candidates.sort(Comparator.comparing(CandidateSource::score).reversed());
        return candidates;
    }

    /**
     * 计算候选源仓评分。
     */
    private BigDecimal scoreCandidate(InventoryRisk risk, WarehouseInventory inv,
                                      Warehouse sourceWh, Warehouse targetWh, int transferableStock) {
        BigDecimal score = BigDecimal.ZERO;

        // 风险等级因子
        String level = risk.getRiskLevel();
        if (RiskLevel.HIGH.name().equals(level)) {
            score = score.add(BigDecimal.valueOf(50));
        } else if (RiskLevel.MEDIUM.name().equals(level)) {
            score = score.add(BigDecimal.valueOf(30));
        } else {
            score = score.add(BigDecimal.valueOf(10));
        }

        // supportDays 越低，分越高
        BigDecimal sd = risk.getSupportDays();
        if (sd != null) {
            if (sd.compareTo(BigDecimal.valueOf(1)) <= 0) {
                score = score.add(BigDecimal.valueOf(20));
            } else if (sd.compareTo(BigDecimal.valueOf(3)) <= 0) {
                score = score.add(BigDecimal.valueOf(12));
            } else if (sd.compareTo(BigDecimal.valueOf(7)) <= 0) {
                score = score.add(BigDecimal.valueOf(6));
            }
        }

        // 仓库优先级
        score = score.add(BigDecimal.valueOf(sourceWh.getPriority() != null ? sourceWh.getPriority() : 0));

        // 可调拨盈余比例
        int available = inv.getAvailableStock() != null ? inv.getAvailableStock() : 1;
        if (available > 0 && transferableStock > 0) {
            BigDecimal ratio = BigDecimal.valueOf(transferableStock)
                    .divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP);
            score = score.add(ratio.multiply(BigDecimal.TEN));
        }

        // 同区域加分
        if (sourceWh.getRegion() != null && sourceWh.getRegion().equals(targetWh.getRegion())) {
            score = score.add(BigDecimal.valueOf(8));
        }
        // 同省加分
        if (sourceWh.getProvince() != null && sourceWh.getProvince().equals(targetWh.getProvince())) {
            score = score.add(BigDecimal.valueOf(5));
        }

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 构建基础 reason 文本。
     */
    private String buildBaseReason(InventoryRisk risk, Warehouse targetWh, WarehouseInventory targetInv,
                                   Warehouse sourceWh, WarehouseInventory sourceInv,
                                   int sourceTransferable, int suggestQty) {
        int targetAvail = targetInv != null && targetInv.getAvailableStock() != null ? targetInv.getAvailableStock() : 0;
        int targetTransit = targetInv != null && targetInv.getInTransitStock() != null ? targetInv.getInTransitStock() : 0;
        int targetSafety = targetInv != null && targetInv.getSafetyStock() != null ? targetInv.getSafetyStock() : 0;
        BigDecimal avgSales = risk.getAvgDailySales();
        BigDecimal sd = risk.getSupportDays();
        int sourceAvail = sourceInv.getAvailableStock() != null ? sourceInv.getAvailableStock() : 0;
        int sourceSafety = sourceInv.getSafetyStock() != null ? sourceInv.getSafetyStock() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("SKU[").append(risk.getSkuId()).append("] 在目标仓[")
                .append(targetWh.getWarehouseName()).append("]存在 ").append(risk.getRiskLevel())
                .append(" 缺货风险：");
        sb.append("当前可用库存 ").append(targetAvail);
        sb.append("，在途 ").append(targetTransit);
        sb.append("，安全库存 ").append(targetSafety);
        if (avgSales != null && avgSales.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("，日均销量 ").append(avgSales.setScale(2, RoundingMode.HALF_UP));
        }
        if (sd != null) {
            sb.append("，可支撑 ").append(sd.setScale(2, RoundingMode.HALF_UP)).append(" 天");
        }
        sb.append("。源仓[").append(sourceWh.getWarehouseName()).append("]");
        sb.append("可用库存 ").append(sourceAvail);
        sb.append("，安全库存 ").append(sourceSafety);
        sb.append("，可调拨 ").append(sourceTransferable);
        sb.append("。建议调拨 ").append(suggestQty).append(" 件");
        if (sd != null && sd.compareTo(BigDecimal.valueOf(3)) <= 0) {
            sb.append("，调拨后可缓解目标仓短期缺货风险");
        }
        sb.append("。");
        return sb.toString();
    }

    /**
     * 构建 AI 解释增强上下文。
     */
    private SuggestionExplainContext buildExplainContext(InventoryRisk risk, Warehouse targetWh,
                                                          WarehouseInventory targetInv,
                                                          Warehouse sourceWh, WarehouseInventory sourceInv,
                                                          int sourceTransferable, int suggestQty,
                                                          BigDecimal score, String baseReason) {
        SuggestionExplainContext ctx = new SuggestionExplainContext();
        ctx.setBaseReason(baseReason);
        ctx.setRiskNo(risk.getRiskNo());
        ctx.setSkuId(risk.getSkuId());
        ctx.setRiskLevel(risk.getRiskLevel());
        ctx.setTargetWarehouseName(targetWh.getWarehouseName());
        ctx.setTargetAvailableStock(targetInv != null ? targetInv.getAvailableStock() : null);
        ctx.setTargetSafetyStock(targetInv != null ? targetInv.getSafetyStock() : null);
        ctx.setTargetInTransitStock(targetInv != null ? targetInv.getInTransitStock() : null);
        ctx.setAvgDailySales(risk.getAvgDailySales());
        ctx.setSupportDays(risk.getSupportDays());
        ctx.setSourceWarehouseName(sourceWh.getWarehouseName());
        ctx.setSourceAvailableStock(sourceInv.getAvailableStock());
        ctx.setSourceSafetyStock(sourceInv.getSafetyStock());
        ctx.setSourceTransferableStock(sourceTransferable);
        ctx.setSuggestQuantity(suggestQty);
        ctx.setScore(score);
        return ctx;
    }

    @Override
    public PageResult<TransferSuggestion> pageSuggestions(PageQuery pageQuery, Long skuId, String status) {
        LambdaQueryWrapper<TransferSuggestion> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) wrapper.eq(TransferSuggestion::getSkuId, skuId);
        if (status != null) wrapper.eq(TransferSuggestion::getStatus, status);
        wrapper.orderByDesc(TransferSuggestion::getCreateTime);
        IPage<TransferSuggestion> page = suggestionMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    public TransferSuggestion getSuggestionById(Long suggestionId) {
        TransferSuggestion s = suggestionMapper.selectById(suggestionId);
        if (s == null) throw new BusinessException("调拨建议不存在: " + suggestionId);
        return s;
    }

    @Override
    public void confirmSuggestion(Long suggestionId) {
        TransferSuggestion s = getSuggestionOrThrow(suggestionId);
        s.setStatus(SuggestionStatus.CONFIRMED.name());
        suggestionMapper.updateById(s);
    }

    @Override
    public void rejectSuggestion(Long suggestionId) {
        TransferSuggestion s = getSuggestionOrThrow(suggestionId);
        s.setStatus(SuggestionStatus.REJECTED.name());
        suggestionMapper.updateById(s);
    }

    @Override
    public void markSuggestionConverted(Long suggestionId) {
        TransferSuggestion s = getSuggestionOrThrow(suggestionId);
        s.setStatus(SuggestionStatus.CONVERTED.name());
        suggestionMapper.updateById(s);
    }

    @Override
    @Transactional
    public String convertToTransferOrder(Long suggestionId) {
        TransferSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null) throw new BusinessException("调拨建议不存在: " + suggestionId);
        if (!SuggestionStatus.CONFIRMED.name().equals(suggestion.getStatus())) {
            throw new BusinessException("只有已确认的建议才能转为调拨单，当前状态: " + suggestion.getStatus());
        }

        String loginId = StpUtil.getLoginIdAsString();
        Long applicantId = null;
        try {
            applicantId = Long.valueOf(loginId);
        } catch (NumberFormatException ignored) {}

        TransferOrder order = transferOrderService.create(
                suggestion.getSkuId(),
                suggestion.getSourceWarehouseId(),
                suggestion.getTargetWarehouseId(),
                suggestion.getSuggestQuantity(),
                applicantId);

        suggestion.setStatus(SuggestionStatus.CONVERTED.name());
        suggestionMapper.updateById(suggestion);

        log.info("建议 {} 已转为调拨单 {}", suggestion.getSuggestionNo(), order.getTransferNo());
        return order.getTransferNo();
    }

    // ==================== 补偿任务 ====================

    @Override
    public CompensationTask createCompensationTask(String bizType, String bizNo, String errorMessage) {
        CompensationTask task = new CompensationTask();
        task.setTaskNo(NoGenerator.generateTaskNo());
        task.setBizType(bizType);
        task.setBizNo(bizNo);
        task.setStatus(com.sits.common.enums.CompensationStatus.PENDING.name());
        task.setRetryCount(0);
        task.setMaxRetryCount(5);
        task.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
        task.setErrorMessage(errorMessage);
        compensationTaskMapper.insert(task);
        log.info("Compensation task created: {} for bizNo={}", task.getTaskNo(), bizNo);
        return task;
    }

    @Override
    public List<CompensationTask> listPendingCompensationTasks(int limit) {
        return compensationTaskMapper.selectList(
                new LambdaQueryWrapper<CompensationTask>()
                        .in(CompensationTask::getStatus,
                                com.sits.common.enums.CompensationStatus.PENDING.name(),
                                com.sits.common.enums.CompensationStatus.RETRYING.name())
                        .le(CompensationTask::getNextRetryTime, LocalDateTime.now())
                        .orderByAsc(CompensationTask::getNextRetryTime)
                        .last("LIMIT " + limit)
        );
    }

    @Override
    public void updateCompensationTask(Long taskId, String status, String errorMessage) {
        CompensationTask task = compensationTaskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("补偿任务不存在: " + taskId);
        task.setStatus(status);
        task.setRetryCount(task.getRetryCount() + 1);
        if (errorMessage != null) task.setErrorMessage(errorMessage);

        if (com.sits.common.enums.CompensationStatus.RETRYING.name().equals(status)
                || com.sits.common.enums.CompensationStatus.PENDING.name().equals(status)) {
            int delayMinutes = (int) Math.pow(2, task.getRetryCount());
            task.setNextRetryTime(LocalDateTime.now().plusMinutes(delayMinutes));
        }
        compensationTaskMapper.updateById(task);
    }

    // ==================== MQ 幂等 ====================

    @Override
    public boolean isEventConsumed(String eventId, String consumerGroup) {
        return mqConsumeRecordMapper.selectCount(
                new LambdaQueryWrapper<MqConsumeRecord>()
                        .eq(MqConsumeRecord::getEventId, eventId)
                        .eq(MqConsumeRecord::getConsumerGroup, consumerGroup)
        ) > 0;
    }

    @Override
    public void recordEventConsumed(String eventId, String consumerGroup,
                                     String bizNo, String eventType) {
        MqConsumeRecord record = new MqConsumeRecord();
        record.setEventId(eventId);
        record.setConsumerGroup(consumerGroup);
        record.setBizNo(bizNo);
        record.setEventType(eventType);
        record.setStatus("CONSUMED");
        try {
            mqConsumeRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("MQ consume record duplicate (idempotent): eventId={}, group={}", eventId, consumerGroup);
        }
    }

    // ==================== 私有辅助方法 ====================

    private BigDecimal computeAvgDailySales(Long skuId, Long warehouseId) {
        int lookbackDays = ruleConfigService.getIntValue(KEY_LOOKBACK_DAYS, 30);
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(lookbackDays - 1);

        List<SalesStatDaily> stats = salesStatMapper.selectList(
                new LambdaQueryWrapper<SalesStatDaily>()
                        .eq(SalesStatDaily::getSkuId, skuId)
                        .eq(SalesStatDaily::getWarehouseId, warehouseId)
                        .between(SalesStatDaily::getStatDate, startDate, endDate)
        );

        if (stats.isEmpty()) return BigDecimal.ZERO;
        int total = stats.stream().mapToInt(s -> s.getSalesQuantity() != null ? s.getSalesQuantity() : 0).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(stats.size()), 2, RoundingMode.HALF_UP);
    }

    private Map<Long, Warehouse> loadWarehouseMap() {
        return warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));
    }

    private TransferSuggestion getSuggestionOrThrow(Long suggestionId) {
        TransferSuggestion s = suggestionMapper.selectById(suggestionId);
        if (s == null) throw new BusinessException("调拨建议不存在: " + suggestionId);
        return s;
    }

    private record CandidateSource(WarehouseInventory inv, Warehouse warehouse,
                                    int transferableStock, BigDecimal score) {}
}
