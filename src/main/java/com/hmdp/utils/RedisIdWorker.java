package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component

public class RedisIdWorker {
    @Resource
    StringRedisTemplate stringRedisTemplate;


    private static  final long BEGIN_TIMESTAMP = 1767225600L;


    public long nextId(String KeyPrefix){

    // 1. Generate timestamp
    LocalDateTime now =LocalDateTime.now();
    long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
    long Timestamp = nowSecond - BEGIN_TIMESTAMP;
    // 2. Generate sequence number
        String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long increment = stringRedisTemplate.opsForValue().increment("icr:" + KeyPrefix + ":" + format);
        // 3. Combine timestamp and sequence number to generate ID
    return Timestamp << 32 | increment;


}
public static void main(String[] args) {
    LocalDateTime date = LocalDateTime.of(2026,1,1,0,0,0);
    long second = date.toEpochSecond(ZoneOffset.UTC);
    System.out.println(second);
}
}
