package com.tongcheng.utils;

public interface ILock {

    Boolean tryLcok(long timeoutSec);

    void unlock();
}
