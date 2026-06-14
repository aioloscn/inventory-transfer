package com.sits.common.exception;

/**
 * Exception for invalid state machine transitions.
 */
public class StateTransitionException extends BusinessException {

    public StateTransitionException(String message) {
        super(400, message);
    }

    public StateTransitionException(String currentStatus, String event) {
        super(400, String.format("状态流转不允许: 当前=%s, 事件=%s", currentStatus, event));
    }
}
