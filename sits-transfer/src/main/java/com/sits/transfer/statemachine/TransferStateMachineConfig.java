package com.sits.transfer.statemachine;

import com.sits.common.enums.TransferOrderEvent;
import com.sits.common.enums.TransferOrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;

import java.util.EnumSet;

/**
 * Spring StateMachine configuration for TransferOrder lifecycle.
 *
 * <p>State transitions as defined in the product design document:
 * <pre>
 * CREATED      + LOCK_STOCK       -> STOCK_LOCKED
 * STOCK_LOCKED + SUBMIT_APPROVAL  -> APPROVING
 * APPROVING    + APPROVE          -> APPROVED
 * APPROVING    + REJECT           -> REJECTED
 * APPROVED     + START_OUTBOUND   -> OUTBOUNDING
 * OUTBOUNDING  + OUTBOUND_SUCCESS -> OUTBOUNDED
 * OUTBOUNDED   + START_SHIP       -> IN_TRANSIT
 * IN_TRANSIT   + START_INBOUND    -> INBOUNDING
 * INBOUNDING   + INBOUND_SUCCESS  -> COMPLETED
 * CREATED      + CANCEL           -> CANCELLED
 * STOCK_LOCKED + CANCEL           -> CANCELLED
 * Any          + FAIL             -> FAILED
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
                // Normal flow
                .withExternal()
                    .source(TransferOrderStatus.CREATED)
                    .target(TransferOrderStatus.STOCK_LOCKED)
                    .event(TransferOrderEvent.LOCK_STOCK)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.STOCK_LOCKED)
                    .target(TransferOrderStatus.APPROVING)
                    .event(TransferOrderEvent.SUBMIT_APPROVAL)
                    .and()
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

                // Cancellation (only from CREATED or STOCK_LOCKED)
                .withExternal()
                    .source(TransferOrderStatus.CREATED)
                    .target(TransferOrderStatus.CANCELLED)
                    .event(TransferOrderEvent.CANCEL)
                    .and()
                .withExternal()
                    .source(TransferOrderStatus.STOCK_LOCKED)
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
