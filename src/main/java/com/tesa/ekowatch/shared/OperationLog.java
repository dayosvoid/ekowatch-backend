package com.tesa.ekowatch.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.function.Supplier;

/** Fixed operation/outcome labels only. Never pass payloads, identities or exception text. */
public final class OperationLog {
    private static final Logger LOG=LoggerFactory.getLogger(OperationLog.class);
    private OperationLog() {}
    public static <T> T record(String operation, Supplier<T> action) {
        String requestId=MDC.get("requestId");
        if(TransactionSynchronizationManager.isActualTransactionActive() &&
                TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    emit(operation,status==STATUS_COMMITTED ? "committed" : status==STATUS_ROLLED_BACK ? "rolled_back" : "unknown",requestId);
                }
            });
            return action.get();
        }
        try {
            T result=action.get(); emit(operation,"returned",requestId); return result;
        } catch(RuleViolation e) {
            emit(operation,"rejected",requestId); throw e;
        } catch(RuntimeException e) {
            emit(operation,"failed",requestId); throw e;
        }
    }
    private static void emit(String operation,String outcome,String requestId) {
        String previous=MDC.get("requestId");
        try {
            if(requestId==null) MDC.remove("requestId"); else MDC.put("requestId",requestId);
            LOG.info("operation={} outcome={}",operation,outcome);
        } finally {
            if(previous==null) MDC.remove("requestId"); else MDC.put("requestId",previous);
        }
    }
}
