package com.sits.common.exception;

/**
 * Exception thrown when stock is insufficient for the requested operation.
 */
public class StockInsufficientException extends BusinessException {

    public StockInsufficientException(String message) {
        super(400, message);
    }

    public StockInsufficientException(Long skuId, Long warehouseId, int required, int available) {
        super(400, String.format("库存不足: skuId=%d, warehouseId=%d, 需要=%d, 可用=%d",
                skuId, warehouseId, required, available));
    }
}
