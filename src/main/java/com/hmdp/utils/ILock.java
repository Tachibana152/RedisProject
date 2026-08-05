package com.hmdp.utils;

public interface ILock {

    Boolean tryLcok(long timeoutSec);

    void unlock();
}
