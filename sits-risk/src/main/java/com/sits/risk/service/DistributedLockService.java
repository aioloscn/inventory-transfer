package com.sits.risk.service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务接口。
 */
public interface DistributedLockService {

    /**
     * 尝试获取锁。
     *
     * @param lockKey   锁的 key
     * @param waitTime  最长等待时间
     * @param leaseTime 锁持有时间
     * @param timeUnit  时间单位
     * @return 是否获取成功
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit);

    /**
     * 释放锁。
     *
     * @param lockKey 锁的 key
     */
    void unlock(String lockKey);
}
