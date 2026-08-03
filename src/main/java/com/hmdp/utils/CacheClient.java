package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        String json = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, json, time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String Key_prefix , ID id , Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit timeUnit)
    {
        String key = Key_prefix + id.toString();
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(json))
        {
            return JSONUtil.toBean(json, type);
        }
        if(json != null)
        {
            return null;
        }
//        //获取互斥锁
//        String lockKey = "lock:shop:" + id;
//        boolean isLock = tryLock(lockKey);
//        //判断是否获取成功
//        //失败，则休眠并重试
//        if(!isLock)
//        {
//            try {
//                Thread.sleep(50);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//            return queryWithPassMutex(id);
//        }
//        //成功，查询数据库并写入redis

        R r = dbFallback.apply(id);
        if(r==null)
        {
            //面对空值，设置redis缓存，存储对应id为空，防止缓存穿透。
            stringRedisTemplate.opsForValue().set(key, "",2, TimeUnit.MINUTES);
            return null;
        }
        //stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r),time, timeUnit);
        //释放互斥锁
        this.set(key,r,time,timeUnit);
        return r;
    }


}
