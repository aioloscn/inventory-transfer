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
import com.sits.risk.dto.RiskScanResult;
import com.sits.risk.entity.CompensationTask;
import com.sits.risk.entity.InventoryRisk;
import com.sits.risk.entity.MqConsumeRecord;
import com.sits.risk.mapper.CompensationTaskMapper;
import com.sits.risk.mapper.InventoryRiskMapper;
import com.sits.risk.mapper.MqConsumeRecordMapper;
import com.sits.risk.service.DistributedLockService;
import com.sits.risk.service.RiskService;
import com.sits.risk.service.RuleConfigService;
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
                           DistributedLockService lockService) {
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

    @Override
    @Transactional
    public List<TransferSuggestion> generateSuggestions() {
        log.info("Generating transfer suggestions...");

        List<InventoryRisk> shortageRisks = riskMapper.selectList(
                new LambdaQueryWrapper<InventoryRisk>()
                        .eq(InventoryRisk::getRiskType, RiskType.SHORTAGE.name())
                        .eq(InventoryRisk::getStatus, RiskStatus.NEW.name())
        );

        Map<Long, Warehouse> warehouseMap = loadWarehouseMap();
        List<TransferSuggestion> suggestions = new ArrayList<>();

        for (InventoryRisk risk : shortageRisks) {
            Warehouse targetWarehouse = warehouseMap.get(risk.getWarehouseId());
            if (targetWarehouse == null) continue;

            List<WarehouseInventory> inventories = inventoryMapper.selectList(
                    new LambdaQueryWrapper<WarehouseInventory>()
                            .eq(WarehouseInventory::getSkuId, risk.getSkuId())
            );

            int shortageQty = getShortageQuantity(risk);
            if (shortageQty <= 0) continue;

            List<CandidateSource> candidates = new ArrayList<>();
            for (WarehouseInventory inv : inventories) {
                if (inv.getWarehouseId().equals(risk.getWarehouseId())) continue;
                int surplus = getSurplus(inv);
                if (surplus < shortageQty) continue;
                Warehouse sourceWh = warehouseMap.get(inv.getWarehouseId());
                if (sourceWh == null) continue;
                int suggestQty = Math.min(surplus, shortageQty);
                BigDecimal score = computeScore(risk, inv, sourceWh, targetWarehouse, suggestQty);
                candidates.add(new CandidateSource(inv, sourceWh, suggestQty, score));
            }

            candidates.sort(Comparator.comparing(CandidateSource::score).reversed());
            if (candidates.isEmpty()) {
                log.info("No suitable source warehouse for risk: {}", risk.getRiskNo());
                continue;
            }

            CandidateSource best = candidates.get(0);

            TransferSuggestion suggestion = new TransferSuggestion();
            suggestion.setSuggestionNo(NoGenerator.generateSuggestionNo());
            suggestion.setRiskId(risk.getId());
            suggestion.setSkuId(risk.getSkuId());
            suggestion.setSourceWarehouseId(best.inv().getWarehouseId());
            suggestion.setTargetWarehouseId(risk.getWarehouseId());
            suggestion.setSuggestQuantity(best.quantity());
            suggestion.setScore(best.score());
            suggestion.setReason(String.format(
                    "仓库[%s] %s 库存不足(可支撑%s天)，建议从仓库[%s]调拨%d件",
                    targetWarehouse.getWarehouseName(), risk.getSkuId(),
                    risk.getSupportDays(), best.warehouse().getWarehouseName(), best.quantity()));
            suggestion.setStatus(SuggestionStatus.GENERATED.name());
            int expireDays = ruleConfigService.getIntValue(KEY_SUGGESTION_EXPIRE, 7);
            suggestion.setExpireTime(LocalDateTime.now().plusDays(expireDays));

            suggestionMapper.insert(suggestion);
            suggestions.add(suggestion);

            risk.setStatus(RiskStatus.PROCESSING.name());
            riskMapper.updateById(risk);
        }

        log.info("Generated {} transfer suggestions.", suggestions.size());
        return suggestions;
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

    private int getShortageQuantity(InventoryRisk risk) {
        WarehouseInventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<WarehouseInventory>()
                        .eq(WarehouseInventory::getSkuId, risk.getSkuId())
                        .eq(WarehouseInventory::getWarehouseId, risk.getWarehouseId())
        );
        if (inv == null) return 0;
        int safety = inv.getSafetyStock() != null ? inv.getSafetyStock() : 0;
        int available = inv.getAvailableStock() != null ? inv.getAvailableStock() : 0;
        return Math.max(0, safety - available);
    }

    private int getSurplus(WarehouseInventory inv) {
        int safety = inv.getSafetyStock() != null ? inv.getSafetyStock() : 0;
        int available = inv.getAvailableStock() != null ? inv.getAvailableStock() : 0;
        int inTransit = inv.getInTransitStock() != null ? inv.getInTransitStock() : 0;
        return Math.max(0, available - safety - inTransit);
    }

    private BigDecimal computeScore(InventoryRisk risk, WarehouseInventory inv,
                                     Warehouse sourceWh, Warehouse targetWh, int suggestQty) {
        BigDecimal score = BigDecimal.ZERO;

        BigDecimal supportDays = risk.getSupportDays() != null ? risk.getSupportDays() : BigDecimal.ZERO;
        if (supportDays.compareTo(BigDecimal.valueOf(1)) <= 0) {
            score = score.add(BigDecimal.valueOf(50));
        } else if (supportDays.compareTo(BigDecimal.valueOf(3)) <= 0) {
            score = score.add(BigDecimal.valueOf(30));
        } else {
            score = score.add(BigDecimal.valueOf(10));
        }

        score = score.add(BigDecimal.valueOf(sourceWh.getPriority() != null ? sourceWh.getPriority() : 0));

        int surplus = getSurplus(inv);
        if (surplus > 0) {
            BigDecimal ratio = BigDecimal.valueOf(surplus)
                    .divide(BigDecimal.valueOf(Math.max(1, inv.getAvailableStock() != null ? inv.getAvailableStock() : 1)),
                            2, RoundingMode.HALF_UP);
            score = score.add(ratio.multiply(BigDecimal.TEN));
        }

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private TransferSuggestion getSuggestionOrThrow(Long suggestionId) {
        TransferSuggestion s = suggestionMapper.selectById(suggestionId);
        if (s == null) throw new BusinessException("调拨建议不存在: " + suggestionId);
        return s;
    }

    private record CandidateSource(WarehouseInventory inv, Warehouse warehouse,
                                    int quantity, BigDecimal score) {}
}
