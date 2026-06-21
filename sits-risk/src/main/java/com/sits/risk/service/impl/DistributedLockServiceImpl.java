package com.sits.risk.service.impl;

import com.sits.risk.service.DistributedLockService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的分布式锁实现。
 */
@Service
public class DistributedLockServiceImpl implements DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockServiceImpl.class);
    private static final String LOCK_PREFIX = "risk:scan:";

    private final RedissonClient redissonClient;

    public DistributedLockServiceImpl(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (acquired) {
                log.info("Lock acquired: {}", lockKey);
            } else {
                log.warn("Lock failed (another task is running): {}", lockKey);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock interrupted: {}", lockKey);
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.info("Lock released: {}", lockKey);
        }
    }
}
