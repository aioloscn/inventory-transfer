package com.sits.transfer.statemachine;

import com.sits.common.enums.TransferOrderEvent;
import com.sits.common.enums.TransferOrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

/**
 * Spring StateMachine configuration for TransferOrder lifecycle.
 *
 * <p>State transitions (v2 — lock stock on approval, not before):
 * <pre>
 * CREATED    + SUBMIT_APPROVAL  -> APPROVING    (stock validation only, no lock)
 * APPROVING  + APPROVE          -> APPROVED     (conditional lock stock + reservation record)
 * APPROVING  + REJECT           -> REJECTED
 * APPROVED   + START_OUTBOUND   -> OUTBOUNDING
 * OUTBOUNDING + OUTBOUND_SUCCESS -> OUTBOUNDED   (deduct actual stock + write-off reservation)
 * OUTBOUNDED + START_SHIP       -> IN_TRANSIT
 * IN_TRANSIT + START_INBOUND    -> INBOUNDING
 * INBOUNDING + INBOUND_SUCCESS  -> COMPLETED
 * CREATED    + CANCEL           -> CANCELLED
 * APPROVING  + CANCEL           -> CANCELLED
 * APPROVED   + CANCEL           -> CANCELLED    (release reservation)
 * Any        + FAIL             -> FAILED
 * </pre>
 */
@Configuration
@EnableStateMachineFactory(name = "transferOrderStateMachineFactory")
public class TransferStateMachineConfig
        extends StateMachineConfigurerAdapter<TransferOrderStatus, TransferOrderEvent> {

    private static final Logger log = LoggerFactory.getLogger(TransferStateMachineConfig.class);

    @Override
    public void configure(StateMachineStateConfigurer<TransferOrderStatus, TransferOrderEvent> states)
            throws Exception {
        states.withStates()
                .initial(TransferOrderStatus.CREATED)
                .end(TransferOrderStatus.COMPLETED)
                .end(TransferOrderStatus.CANCELLED)
                .end(TransferOrderStatus.FAILED)
                .states(EnumSet.allOf(TransferOrderStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<TransferOrderStatus, TransferOrderEvent> transitions)
            throws Exception {
        transitions
                // Normal flow: CREATED -> APPROVING (submit for approval, validate stock only)
                .withExternal()
                    .source(TransferOrderStatus.CREATED)
                    .target(TransferOrderStatus.APPROVING)
                    .event(TransferOrderEvent.SUBMIT_APPROVAL)
                    .and()
                // APPROVING -> APPROVED (lock stock within transaction)
                .withExternal()
                    .source(TransferOrderStatus.APPROVING)
                    .target(TransferOrderStatus.APPROVED)
                    .event(TransferOrderEvent.APPROVE)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.APPROVED)
                    .target(TransferOrderStatus.OUTBOUNDING)
                    .event(TransferOrderEvent.START_OUTBOUND)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.OUTBOUNDING)
                    .target(TransferOrderStatus.OUTBOUNDED)
                    .event(TransferOrderEvent.OUTBOUND_SUCCESS)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.OUTBOUNDED)
                    .target(TransferOrderStatus.IN_TRANSIT)
                    .event(TransferOrderEvent.START_SHIP)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.IN_TRANSIT)
                    .target(TransferOrderStatus.INBOUNDING)
                    .event(TransferOrderEvent.START_INBOUND)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.INBOUNDING)
                    .target(TransferOrderStatus.COMPLETED)
                    .event(TransferOrderEvent.INBOUND_SUCCESS)
                    .and()

                // Rejection
                .withExternal()
                    .source(TransferOrderStatus.APPROVING)
                    .target(TransferOrderStatus.REJECTED)
                    .event(TransferOrderEvent.REJECT)
                    .and()

                // Cancellation
                .withExternal()
                    .source(TransferOrderStatus.CREATED)
                    .target(TransferOrderStatus.CANCELLED)
                    .event(TransferOrderEvent.CANCEL)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.APPROVING)
                    .target(TransferOrderStatus.CANCELLED)
                    .event(TransferOrderEvent.CANCEL)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.APPROVED)
                    .target(TransferOrderStatus.CANCELLED)
                    .event(TransferOrderEvent.CANCEL)
                    .and()

                // Failure (from intermediate states)
                .withExternal()
                    .source(TransferOrderStatus.OUTBOUNDING)
                    .target(TransferOrderStatus.FAILED)
                    .event(TransferOrderEvent.FAIL)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.INBOUNDING)
                    .target(TransferOrderStatus.FAILED)
                    .event(TransferOrderEvent.FAIL)
        ;
    }
}
