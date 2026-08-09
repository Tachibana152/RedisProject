package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
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
       //Shop shop = cacheClient.queryWithPassThrough("cache:shop:", id, Shop.class, this::getById, 30L, TimeUnit.MINUTES);
        //加上互斥锁，再解决缓存穿透的基础上解决了缓存击穿
//        Shop shop = queryWithPassMutex(id);
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire("cache:shop:", id, Shop.class, this::getById, 30L, TimeUnit.MINUTES);
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
    public Result saveShop(Shop shop) {
        // 1.写入数据库
        boolean save = save(shop);
        if (!save) {
            return Result.fail("新增店铺失败");
        }
        // 2.同步写入 GEO（GEO key 已预热时立即生效；未预热时首次查询的 loadShopGeo 会全量加载，包含此店）
        updateShopGeo(shop);
        return Result.ok(shop.getId());
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
        // 同步更新 GEO 坐标（GEOADD 同名 member 会覆盖旧坐标）
        updateShopGeo(shop);
        return Result.ok(shop);
    }

    /**
     * 同步单个店铺的 GEO 坐标：仅当该类型 GEO key 已预热时写入，避免冷 key 残留脏数据
     * @param shop 店铺（需含 id、typeId、x、y）
     */
    private void updateShopGeo(Shop shop) {
        if (shop == null || shop.getId() == null || shop.getTypeId() == null
                || shop.getX() == null || shop.getY() == null) {
            return;
        }
        String key = RedisConstants.SHOP_GEO_KEY + shop.getTypeId();
        // GEO key 未预热（不存在）时不写入，等 loadShopGeo 全量加载即可
        if (!BooleanUtil.isTrue(stringRedisTemplate.hasKey(key))) {
            return;
        }
        // GEOADD key lon lat member（同 member 重复添加会自动覆盖坐标）
        // 注意：2.7.x 的 GeoOperations.add 无 add(key,member,x,y) 重载，需用 add(key, Point, member)
        stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，走数据库普通分页（sortBy 非空时按该字段降序）
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .orderByDesc(StrUtil.isNotBlank(sortBy), sortBy)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序。结果：shopId、distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        // 3.1.GEO 缓存预热：key 不存在时从数据库加载该类型全部商铺坐标
        loadShopGeo(Long.valueOf(typeId), key);
        // GEOSEARCH key FROMLONLAT x y BYRADIUS 15 km WITHDISTANCE ASC
        // 注意：搜索半径通过 search 的第 3 个参数（Distance）传入，GeoSearchCommandArgs 中没有 geoRadius 方法
        // 半径 15km：覆盖杭州城区范围。不设 limit，取出半径内全部，便于在内存中按 sortBy 二次排序后再分页
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(new Point(x, y)),
                        new Distance(15, RedisGeoCommands.DistanceUnit.KILOMETERS),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()
                                .sortAscending()
                );
        // 4.解析出全部 id 与距离
        if (results == null || results.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : list) {
            // 4.1.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.2.获取距离
            distanceMap.put(shopIdStr, result.getDistance());
        }
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            // Redis GEO 返回距离单位为查询时指定的 KILOMETERS，这里转为米，
            // 与前端模板（s.distance < 1000 显示 m，否则显示 km）保持一致
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue() * 1000);
        }
        // 5.1.按排序字段二次排序（人气 comments / 评分 score 降序；空则保持 GEO 距离升序）
        if ("comments".equals(sortBy)) {
            shops.sort(Comparator.comparing(Shop::getComments,
                    Comparator.nullsFirst(Comparator.reverseOrder())));
        } else if ("score".equals(sortBy)) {
            shops.sort(Comparator.comparing(Shop::getScore,
                    Comparator.nullsFirst(Comparator.reverseOrder())));
        }
        // 5.2.内存分页，截取当前页
        List<Shop> pageShops = shops.stream()
                .skip(from)
                .limit(SystemConstants.DEFAULT_PAGE_SIZE)
                .collect(Collectors.toList());
        // 当前页无数据（页码超出范围），返回空列表
        if (pageShops.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 6.返回
        return Result.ok(pageShops);
    }

    /**
     * GEO 缓存预热：将指定类型的全部商铺经纬度写入 Redis GEO（key 不存在时）
     * @param typeId 商铺类型id
     * @param key GEO key（shop:geo:{typeId}）
     */
    private void loadShopGeo(Long typeId, String key) {
        // 判断 GEO key 是否已存在，已存在则无需重复写入
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        if (BooleanUtil.isTrue(hasKey)) {
            return;
        }
        // 查询该类型全部商铺
        List<Shop> shops = query().eq("type_id", typeId).list();
        if (shops == null || shops.isEmpty()) {
            return;
        }
        // 组装 <商铺id, 坐标> 并批量写入 GEO（GEOADD）
        Map<String, Point> pointMap = shops.stream()
                .collect(Collectors.toMap(
                        shop -> shop.getId().toString(),
                        shop -> new Point(shop.getX(), shop.getY())
                ));
        stringRedisTemplate.opsForGeo().add(key, pointMap);
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
