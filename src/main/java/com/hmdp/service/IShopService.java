package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result saveShop(Shop shop);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y);
}
