package com.sits.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sits.common.enums.InventoryBizType;
import com.sits.common.enums.InventoryChangeType;
import com.sits.common.exception.BusinessException;
import com.sits.common.exception.StockConcurrentException;
import com.sits.common.exception.StockInsufficientException;
import com.sits.common.util.NoGenerator;
import com.sits.inventory.entity.InventoryFlow;
import com.sits.inventory.entity.WarehouseInventory;
import com.sits.inventory.mapper.InventoryFlowMapper;
import com.sits.inventory.mapper.WarehouseInventoryMapper;
import com.sits.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Inventory service implementation.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Uses MyBatis-Plus {@code @Version} for optimistic locking — avoids oversell
 *       without a heavy pessimistic lock.</li>
 *   <li>Inventory flow is recorded BEFORE the stock update happens — the flow table's
 *       unique constraint (biz_type, biz_no, change_type) provides idempotency.</li>
 *   <li>If the optimistic lock update returns 0 rows, a {@link StockConcurrentException}
 *       is thrown — the caller should retry.</li>
 * </ul>
 */
@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final WarehouseInventoryMapper inventoryMapper;
    private final InventoryFlowMapper flowMapper;

    public InventoryServiceImpl(WarehouseInventoryMapper inventoryMapper,
                                InventoryFlowMapper flowMapper) {
        this.inventoryMapper = inventoryMapper;
        this.flowMapper = flowMapper;
    }

    @Override
    public WarehouseInventory getBySkuAndWarehouse(Long skuId, Long warehouseId) {
        return inventoryMapper.selectOne(
                new LambdaQueryWrapper<WarehouseInventory>()
                        .eq(WarehouseInventory::getSkuId, skuId)
                        .eq(WarehouseInventory::getWarehouseId, warehouseId)
        );
    }

    @Override
    public List<WarehouseInventory> listByWarehouse(Long warehouseId) {
        return inventoryMapper.selectList(
                new LambdaQueryWrapper<WarehouseInventory>()
                        .eq(WarehouseInventory::getWarehouseId, warehouseId)
        );
    }

    // ---------------------------------------------------------------
    // Lock stock (available -> locked)
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void lockStock(Long skuId, Long warehouseId, int quantity,
                           String transferNo, String operator) {
        log.info("Locking stock: skuId={}, warehouseId={}, qty={}, transferNo={}",
                skuId, warehouseId, quantity, transferNo);

        // 1) Record flow first (idempotent via unique constraint)
        WarehouseInventory inv = getOrThrow(skuId, warehouseId);
        insertFlow(inv, InventoryBizType.TRANSFER, transferNo,
                InventoryChangeType.TRANSFER_LOCK, -quantity, operator);

        // 2) Optimistic lock update
        LambdaUpdateWrapper<WarehouseInventory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WarehouseInventory::getSkuId, skuId)
                .eq(WarehouseInventory::getWarehouseId, warehouseId)
                .eq(WarehouseInventory::getVersion, inv.getVersion())
                .ge(WarehouseInventory::getAvailableStock, quantity)   // prevent negative
                .setSql("available_stock = available_stock - " + quantity)
                .setSql("locked_stock = locked_stock + " + quantity);

        int rows = inventoryMapper.update(null, wrapper);
        if (rows == 0) {
            throw new StockConcurrentException(skuId, warehouseId);
        }

        log.info("Stock locked: skuId={}, warehouseId={}, qty={}", skuId, warehouseId, quantity);
    }

    // ---------------------------------------------------------------
    // Unlock stock (locked -> available)
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void unlockStock(Long skuId, Long warehouseId, int quantity,
                             String transferNo, String operator) {
        log.info("Unlocking stock: skuId={}, warehouseId={}, qty={}", skuId, warehouseId, quantity);

        WarehouseInventory inv = getOrThrow(skuId, warehouseId);
        insertFlow(inv, InventoryBizType.TRANSFER, transferNo,
                InventoryChangeType.TRANSFER_UNLOCK, quantity, operator);

        LambdaUpdateWrapper<WarehouseInventory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WarehouseInventory::getSkuId, skuId)
                .eq(WarehouseInventory::getWarehouseId, warehouseId)
                .eq(WarehouseInventory::getVersion, inv.getVersion())
                .ge(WarehouseInventory::getLockedStock, quantity)
                .setSql("locked_stock = locked_stock - " + quantity)
                .setSql("available_stock = available_stock + " + quantity);

        int rows = inventoryMapper.update(null, wrapper);
        if (rows == 0) {
            throw new StockConcurrentException(skuId, warehouseId);
        }
    }

    // ---------------------------------------------------------------
    // Outbound (locked -> in-transit)
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void outboundStock(Long skuId, Long warehouseId, int quantity,
                               String transferNo, String operator) {
        log.info("Outbound stock: skuId={}, warehouseId={}, qty={}", skuId, warehouseId, quantity);

        WarehouseInventory inv = getOrThrow(skuId, warehouseId);
        insertFlow(inv, InventoryBizType.TRANSFER, transferNo,
                InventoryChangeType.TRANSFER_OUTBOUND, -quantity, operator);

        LambdaUpdateWrapper<WarehouseInventory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WarehouseInventory::getSkuId, skuId)
                .eq(WarehouseInventory::getWarehouseId, warehouseId)
                .eq(WarehouseInventory::getVersion, inv.getVersion())
                .ge(WarehouseInventory::getLockedStock, quantity)
                .setSql("locked_stock = locked_stock - " + quantity)
                .setSql("in_transit_stock = in_transit_stock + " + quantity);

        int rows = inventoryMapper.update(null, wrapper);
        if (rows == 0) {
            throw new StockConcurrentException(skuId, warehouseId);
        }
    }

    // ---------------------------------------------------------------
    // Inbound (available += quantity at target warehouse)
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void inboundStock(Long skuId, Long warehouseId, int quantity,
                              String transferNo, String operator) {
        log.info("Inbound stock: skuId={}, warehouseId={}, qty={}", skuId, warehouseId, quantity);

        WarehouseInventory inv = getOrThrow(skuId, warehouseId);
        insertFlow(inv, InventoryBizType.TRANSFER, transferNo,
                InventoryChangeType.TRANSFER_INBOUND, quantity, operator);

        LambdaUpdateWrapper<WarehouseInventory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WarehouseInventory::getSkuId, skuId)
                .eq(WarehouseInventory::getWarehouseId, warehouseId)
                .eq(WarehouseInventory::getVersion, inv.getVersion())
                .setSql("available_stock = available_stock + " + quantity);

        int rows = inventoryMapper.update(null, wrapper);
        if (rows == 0) {
            throw new StockConcurrentException(skuId, warehouseId);
        }
    }

    // ---------------------------------------------------------------
    // Release in-transit stock at source warehouse after inbound
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void releaseInTransitStock(Long skuId, Long warehouseId, int quantity,
                                       String transferNo, String operator) {
        log.info("Release in-transit stock: skuId={}, warehouseId={}, qty={}", skuId, warehouseId, quantity);

        WarehouseInventory inv = getOrThrow(skuId, warehouseId);
        // No dedicated flow record for release — covered by inbound flow at target warehouse

        LambdaUpdateWrapper<WarehouseInventory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WarehouseInventory::getSkuId, skuId)
                .eq(WarehouseInventory::getWarehouseId, warehouseId)
                .eq(WarehouseInventory::getVersion, inv.getVersion())
                .ge(WarehouseInventory::getInTransitStock, quantity)
                .setSql("in_transit_stock = in_transit_stock - " + quantity);

        int rows = inventoryMapper.update(null, wrapper);
        if (rows == 0) {
            throw new StockConcurrentException(skuId, warehouseId);
        }
    }

    @Override
    public List<InventoryFlow> listFlowsByBizNo(String bizNo) {
        return flowMapper.selectList(
                new LambdaQueryWrapper<InventoryFlow>()
                        .eq(InventoryFlow::getBizNo, bizNo)
                        .orderByDesc(InventoryFlow::getCreateTime)
        );
    }

    // ==================== Private Helpers ====================

    private WarehouseInventory getOrThrow(Long skuId, Long warehouseId) {
        WarehouseInventory inv = getBySkuAndWarehouse(skuId, warehouseId);
        if (inv == null) {
            throw new BusinessException(String.format(
                    "库存记录不存在: skuId=%d, warehouseId=%d", skuId, warehouseId));
        }
        return inv;
    }

    private void insertFlow(WarehouseInventory inv, InventoryBizType bizType,
                            String bizNo, InventoryChangeType changeType,
                            int changeQuantity, String operator) {
        InventoryFlow flow = new InventoryFlow();
        flow.setFlowNo(NoGenerator.generateFlowNo());
        flow.setSkuId(inv.getSkuId());
        flow.setWarehouseId(inv.getWarehouseId());
        flow.setBizType(bizType.name());
        flow.setBizNo(bizNo);
        flow.setChangeType(changeType.name());
        flow.setBeforeAvailable(inv.getAvailableStock());
        flow.setChangeQuantity(changeQuantity);
        flow.setAfterAvailable(inv.getAvailableStock() + changeQuantity);
        flow.setOperator(operator);

        try {
            flowMapper.insert(flow);
        } catch (Exception e) {
            // If duplicate, it's idempotent — just log and continue
            log.warn("Inventory flow duplicate (idempotent): bizType={}, bizNo={}, changeType={}",
                    bizType, bizNo, changeType);
            throw new BusinessException("库存流水重复，操作幂等跳过: " + bizNo);
        }
    }
}
