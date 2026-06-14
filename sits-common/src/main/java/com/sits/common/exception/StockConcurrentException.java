package com.sits.common.exception;

/**
 * Exception thrown when concurrent stock operations conflict.
 */
public class StockConcurrentException extends BusinessException {

    public StockConcurrentException(String message) {
        super(409, message);
    }

    public StockConcurrentException(Long skuId, Long warehouseId) {
        super(409, String.format("库存并发冲突: skuId=%d, warehouseId=%d", skuId, warehouseId));
    }
}
