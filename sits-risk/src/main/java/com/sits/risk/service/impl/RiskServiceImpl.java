package com.sits.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sits.admin.entity.Warehouse;
import com.sits.admin.mapper.WarehouseMapper;
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
import com.sits.risk.entity.CompensationTask;
import com.sits.risk.entity.InventoryRisk;
import com.sits.risk.entity.MqConsumeRecord;
import com.sits.risk.mapper.CompensationTaskMapper;
import com.sits.risk.mapper.InventoryRiskMapper;
import com.sits.risk.mapper.MqConsumeRecordMapper;
import com.sits.risk.service.RiskService;
import com.sits.transfer.entity.TransferSuggestion;
import com.sits.transfer.mapper.TransferSuggestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RiskService implementation.
 *
 * <p>Core algorithms:
 * <ul>
 *   <li><b>Risk scanning:</b> iterates all inventory records, computes avg_daily_sales
 *       from the sales stat table (last 30 days), then determines shortage/overstock.</li>
 *   <li><b>Suggestion generation:</b> for each unresolved shortage risk, finds the
 *       best source warehouse (highest surplus, lowest cost) and generates a scored
 *       transfer suggestion.</li>
 * </ul>
 */
@Service
public class RiskServiceImpl implements RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskServiceImpl.class);

    /** Thresholds — could be moved to a config table later */
    private static final int SHORTAGE_DAYS_HIGH = 3;
    private static final int SHORTAGE_DAYS_MEDIUM = 7;
    private static final int OVERSTOCK_DAYS = 30;
    private static final BigDecimal OVERSTOCK_RATIO = BigDecimal.valueOf(0.8);
    private static final int AVG_SALES_LOOKBACK_DAYS = 30;

    private final InventoryRiskMapper riskMapper;
    private final WarehouseInventoryMapper inventoryMapper;
    private final SalesStatDailyMapper salesStatMapper;
    private final TransferSuggestionMapper suggestionMapper;
    private final CompensationTaskMapper compensationTaskMapper;
    private final MqConsumeRecordMapper mqConsumeRecordMapper;
    private final WarehouseMapper warehouseMapper;

    public RiskServiceImpl(InventoryRiskMapper riskMapper,
                           WarehouseInventoryMapper inventoryMapper,
                           SalesStatDailyMapper salesStatMapper,
                           TransferSuggestionMapper suggestionMapper,
                           CompensationTaskMapper compensationTaskMapper,
                           MqConsumeRecordMapper mqConsumeRecordMapper,
                           WarehouseMapper warehouseMapper) {
        this.riskMapper = riskMapper;
        this.inventoryMapper = inventoryMapper;
        this.salesStatMapper = salesStatMapper;
        this.suggestionMapper = suggestionMapper;
        this.compensationTaskMapper = compensationTaskMapper;
        this.mqConsumeRecordMapper = mqConsumeRecordMapper;
        this.warehouseMapper = warehouseMapper;
    }

    // ==================== Risk Scan ====================

    @Override
    @Transactional
    public List<InventoryRisk> scanRisks() {
        log.info("Starting inventory risk scan...");

        List<WarehouseInventory> allInventory = inventoryMapper.selectList(null);
        Map<Long, Warehouse> warehouseMap = loadWarehouseMap();
        List<InventoryRisk> risks = new ArrayList<>();

        for (WarehouseInventory inv : allInventory) {
            // Skip if no safety stock or max stock configured
            if (inv.getSafetyStock() == null || inv.getSafetyStock() == 0) continue;

            // Compute avg daily sales (last 30 days)
            BigDecimal avgDailySales = computeAvgDailySales(inv.getSkuId(), inv.getWarehouseId());

            int available = inv.getAvailableStock() != null ? inv.getAvailableStock() : 0;
            int safety = inv.getSafetyStock();

            // --- Shortage risk ---
            if (available < safety) {
                BigDecimal supportDays = avgDailySales.compareTo(BigDecimal.ZERO) > 0
                        ? BigDecimal.valueOf(available).divide(avgDailySales, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                RiskLevel level;
                if (supportDays.compareTo(BigDecimal.valueOf(SHORTAGE_DAYS_HIGH)) < 0) {
                    level = RiskLevel.HIGH;
                } else if (supportDays.compareTo(BigDecimal.valueOf(SHORTAGE_DAYS_MEDIUM)) < 0) {
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
                risk.setStatus(RiskStatus.NEW.name());
                riskMapper.insert(risk);
                risks.add(risk);

                log.info("Shortage risk: skuId={}, warehouseId={}, available={}, safety={}, days={}",
                        inv.getSkuId(), inv.getWarehouseId(), available, safety, supportDays);
            }

            // --- Overstock risk ---
            if (inv.getMaxStock() != null && inv.getMaxStock() > 0) {
                BigDecimal overstockThreshold = BigDecimal.valueOf(inv.getMaxStock())
                        .multiply(OVERSTOCK_RATIO);
                if (available > overstockThreshold.intValue()
                        && avgDailySales.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal supportDays = BigDecimal.valueOf(available)
                            .divide(avgDailySales, 2, RoundingMode.HALF_UP);
                    if (supportDays.compareTo(BigDecimal.valueOf(OVERSTOCK_DAYS)) > 0) {
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
                        riskMapper.insert(risk);
                        risks.add(risk);

                        log.info("Overstock risk: skuId={}, warehouseId={}, available={}, days={}",
                                inv.getSkuId(), inv.getWarehouseId(), available, supportDays);
                    }
                }
            }
        }

        log.info("Risk scan complete. Found {} risks.", risks.size());
        return risks;
    }

    @Override
    public List<InventoryRisk> listRisks(Long skuId, Long warehouseId, String riskType, String status) {
        LambdaQueryWrapper<InventoryRisk> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) wrapper.eq(InventoryRisk::getSkuId, skuId);
        if (warehouseId != null) wrapper.eq(InventoryRisk::getWarehouseId, warehouseId);
        if (riskType != null) wrapper.eq(InventoryRisk::getRiskType, riskType);
        if (status != null) wrapper.eq(InventoryRisk::getStatus, status);
        wrapper.orderByDesc(InventoryRisk::getCreateTime);
        return riskMapper.selectList(wrapper);
    }

    @Override
    public void updateRiskStatus(Long riskId, String status) {
        InventoryRisk risk = riskMapper.selectById(riskId);
        if (risk == null) throw new BusinessException("风险记录不存在: " + riskId);
        risk.setStatus(status);
        riskMapper.updateById(risk);
    }

    // ==================== Transfer Suggestions ====================

    @Override
    @Transactional
    public List<TransferSuggestion> generateSuggestions() {
        log.info("Generating transfer suggestions...");

        // Only generate for unresolved shortage risks
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

            // Find all warehouses that have surplus for this SKU
            List<WarehouseInventory> inventories = inventoryMapper.selectList(
                    new LambdaQueryWrapper<WarehouseInventory>()
                            .eq(WarehouseInventory::getSkuId, risk.getSkuId())
            );

            // Find source warehouses with surplus (available > safety_stock + suggest_quantity)
            int shortageQty = getShortageQuantity(risk);
            if (shortageQty <= 0) continue;

            // Score each candidate source warehouse
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

            // Pick the best candidate
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
            suggestion.setExpireTime(LocalDateTime.now().plusDays(7));

            suggestionMapper.insert(suggestion);
            suggestions.add(suggestion);

            // Mark risk as PROCESSING
            risk.setStatus(RiskStatus.PROCESSING.name());
            riskMapper.updateById(risk);
        }

        log.info("Generated {} transfer suggestions.", suggestions.size());
        return suggestions;
    }

    @Override
    public List<TransferSuggestion> listSuggestions(Long skuId, String status) {
        LambdaQueryWrapper<TransferSuggestion> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) wrapper.eq(TransferSuggestion::getSkuId, skuId);
        if (status != null) wrapper.eq(TransferSuggestion::getStatus, status);
        wrapper.orderByDesc(TransferSuggestion::getCreateTime);
        return suggestionMapper.selectList(wrapper);
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

    // ==================== Compensation Tasks ====================

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

        // Exponential backoff: 1min, 2min, 4min, 8min, 16min
        if (com.sits.common.enums.CompensationStatus.RETRYING.name().equals(status)
                || com.sits.common.enums.CompensationStatus.PENDING.name().equals(status)) {
            int delayMinutes = (int) Math.pow(2, task.getRetryCount());
            task.setNextRetryTime(LocalDateTime.now().plusMinutes(delayMinutes));
        }

        compensationTaskMapper.updateById(task);
    }

    // ==================== MQ Idempotency ====================

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
            // Duplicate — idempotent, just log
            log.warn("MQ consume record duplicate (idempotent): eventId={}, group={}", eventId, consumerGroup);
        }
    }

    // ==================== Private Helpers ====================

    private Map<Long, Warehouse> loadWarehouseMap() {
        return warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w));
    }

    private BigDecimal computeAvgDailySales(Long skuId, Long warehouseId) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(AVG_SALES_LOOKBACK_DAYS - 1);

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

    private int getShortageQuantity(InventoryRisk risk) {
        // safety_stock - available_stock
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

    /**
     * Score a candidate source warehouse.
     * <p>Higher score = better candidate.
     * Factors: risk urgency (from support_days), warehouse priority, surplus ratio.
     */
    private BigDecimal computeScore(InventoryRisk risk, WarehouseInventory inv,
                                     Warehouse sourceWh, Warehouse targetWh, int suggestQty) {
        BigDecimal score = BigDecimal.ZERO;

        // 1) Urgency bonus — lower support days = higher urgency = higher score
        BigDecimal supportDays = risk.getSupportDays() != null ? risk.getSupportDays() : BigDecimal.ZERO;
        if (supportDays.compareTo(BigDecimal.valueOf(1)) <= 0) {
            score = score.add(BigDecimal.valueOf(50));
        } else if (supportDays.compareTo(BigDecimal.valueOf(3)) <= 0) {
            score = score.add(BigDecimal.valueOf(30));
        } else {
            score = score.add(BigDecimal.valueOf(10));
        }

        // 2) Source warehouse priority (higher = better equipped)
        score = score.add(BigDecimal.valueOf(sourceWh.getPriority() != null ? sourceWh.getPriority() : 0));

        // 3) Surplus ratio — more surplus = higher score
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

    // -- Internal record type --
    private record CandidateSource(WarehouseInventory inv, Warehouse warehouse,
                                    int quantity, BigDecimal score) {}
}
