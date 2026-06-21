package com.sits.common.util;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ID and No generation utility.
 */
public final class NoGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private NoGenerator() {}

    /**
     * Generate a flow number for inventory flows.
     */
    public static String generateFlowNo() {
        return "FLW" + LocalDateTime.now().format(DATE_FORMAT) + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    /**
     * Generate a risk number.
     */
    public static String generateRiskNo() {
        return "RSK" + LocalDateTime.now().format(DATE_FORMAT) + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    /**
     * Generate a transfer suggestion number.
     */
    public static String generateSuggestionNo() {
        return "TSG" + LocalDateTime.now().format(DATE_FORMAT) + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    /**
     * Generate a transfer order number.
     */
    public static String generateTransferNo() {
        return "TRF" + LocalDateTime.now().format(DATE_FORMAT) + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    /**
     * Generate a compensation task number.
     */
    public static String generateTaskNo() {
        return "TSK" + LocalDateTime.now().format(DATE_FORMAT) + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    /**
     * Generate an MQ event ID.
     */
    public static String generateEventId() {
        return "EVT" + IdUtil.fastSimpleUUID().toUpperCase();
    }

    /**
     * Generate a reservation number for inventory reservation.
     */
    public static String generateReservationNo() {
        return "RSV" + LocalDateTime.now().format(DATE_FORMAT) + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }
}
