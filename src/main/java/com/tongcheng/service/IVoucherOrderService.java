package com.tongcheng.service;

import com.tongcheng.dto.Result;
import com.tongcheng.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result secKillVoucher(Long voucherId) throws InterruptedException;

    void createVoucherOrder(VoucherOrder voucherOrder);
}
