package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static io.lettuce.core.KillArgs.Builder.id;

public class SimpleRedisLock implements ILock{
    StringRedisTemplate stringRedisTemplate;
    private String name;
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    @Override
    public Boolean tryLcok(long timeoutSec) {
        String id = ID_PREFIX + Thread.currentThread().getId();

        Boolean b = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, id, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
    }

    @Override
    public void unlock() {
        String id = ID_PREFIX + Thread.currentThread().getId();
        String s = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(id.equals(s))
        {
            stringRedisTemplate.delete(KEY_PREFIX + name);}

    }
}
