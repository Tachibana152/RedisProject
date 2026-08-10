package com.tongcheng.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.tongcheng.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    public <R, ID> R queryWithPassThrough(String Key_prefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = Key_prefix + id.toString();
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
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
        if (r == null) {
            //面对空值，设置redis缓存，存储对应id为空，防止缓存穿透。
            stringRedisTemplate.opsForValue().set(key, "", 2, TimeUnit.MINUTES);
            return null;
        }
        //stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r),time, timeUnit);
        //释放互斥锁
        this.set(key, r, time, timeUnit);
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(String Key_prefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String  key = Key_prefix + id.toString();
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 缓存未命中：逻辑过期方案假设数据已预热，但未命中时兜底查一次数据库并写入逻辑过期缓存，
            // 避免未预热的数据（如 GEO 附近商铺搜索出的店铺）被误判为"不存在"
            R r = dbFallback.apply(id);
            if (r == null) {
                return null;
            }
            this.setWithLogicalExpire(key, r, time, timeUnit);
            return r;
        }
        //命中，先把json反序列化为对象，判断过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期，返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        //过期
        String lockKey = "lock:shop:" + id;
        boolean isLock = tryLock(lockKey);
        //获取互斥锁成功，开启独立线程，实现缓存重建
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private boolean tryLock(String key)
    {
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    private void unlock(String key)
    {
        stringRedisTemplate.delete(key);
    }
}