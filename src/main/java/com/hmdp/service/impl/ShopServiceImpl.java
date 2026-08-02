package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        String s = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        if(s!=null)
        {
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return Result.ok(shop);
        }
        Shop shop = baseMapper.selectById(id);
        if(shop==null)
        {
        return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
