package com.tongcheng.controller;


import com.tongcheng.dto.Result;
import com.tongcheng.service.IVoucherOrderService;
import com.tongcheng.service.IVoucherService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) throws InterruptedException {

        return voucherOrderService.secKillVoucher(voucherId);
    }
}
