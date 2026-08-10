package com.tongcheng.service.impl;

import com.tongcheng.entity.SeckillVoucher;
import com.tongcheng.mapper.SeckillVoucherMapper;
import com.tongcheng.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
