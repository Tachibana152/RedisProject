package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Tachibana
 * @since 2026-8-3
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
       Shop shop = cacheClient.queryWithPassThrough("cache:shop:", id, Shop.class, this::getById, 30L, TimeUnit.MINUTES);
        //加上互斥锁，再解决缓存穿透的基础上解决了缓存击穿
//        Shop shop = queryWithPassMutex(id);
//        Shop shop = queryWithLogicalExpire(id);
        if(shop==null)
        {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithLogicalExpire(Long id){
        String s = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        if(StrUtil.isBlank(s))
        {
            return null;
        }

        //命中，先把json反序列化为对象，判断过期时间
        RedisData redisData = JSONUtil.toBean(s, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期，返回店铺信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }

        //过期
    String lockKey = "lock:shop:" + id;
        boolean isLock = tryLock(lockKey);
        //获取互斥锁成功，开启独立线程，实现缓存重建
        if(isLock)
        {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 解决缓存击穿（互斥锁）
     * @param id
     * @return
     */
    public Shop queryWithPassMutex(Long id){
        String s = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        if(StrUtil.isNotBlank(s))
        {
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return shop;
        }
        if(s != null)
        {
            return null;
        }
        //获取互斥锁
        String lockKey = "lock:shop:" + id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取成功
        //失败，则休眠并重试
        if(!isLock)
        {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return queryWithPassMutex(id);
        }
        //成功，查询数据库并写入redis

        Shop shop = baseMapper.selectById(id);
        if(shop==null)
        {
            //面对空值，设置redis缓存，存储对应id为空，防止缓存穿透。
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, "",2, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        //释放互斥锁
        unlock(lockKey);
        return shop;
    }

    /**
     * 解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        String s = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        if(StrUtil.isNotBlank(s))
        {
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return shop;
        }
        if(s != null)
        {
            return null;
        }
        Shop shop = baseMapper.selectById(id);

        if(shop==null)
        {
            //面对空值，设置redis缓存，存储对应id为空，防止缓存穿透。
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, "",2, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = baseMapper.selectById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(redisData));
      }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null)
        {
            return Result.fail("店铺id不能为空!");
        }
        updateById(shop);
        stringRedisTemplate.delete("cache:shop:" +id);
        return Result.ok(shop);
    }

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
