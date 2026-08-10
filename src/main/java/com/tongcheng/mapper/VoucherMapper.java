package com.tongcheng.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tongcheng.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
