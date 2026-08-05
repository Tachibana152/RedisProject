package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.SneakyThrows;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Autowired
    private VoucherOrderMapper voucherOrderMapper;
    // 注入自身代理：锁外层方法调用事务方法时必须走代理，否则 @Transactional 失效
    @Autowired
    private IVoucherOrderService proxy;

    @Resource
    RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result secKillVoucher(Long voucherId) throws InterruptedException {
        //check
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //judge seckill begin or end
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if(LocalDateTime.now().isBefore(beginTime) || LocalDateTime.now().isAfter(endTime)){
            return Result.fail("秒杀未开始或已结束");
        }

        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 一人一单：对同一用户加锁，锁内调用事务方法（事务提交发生在方法返回前，即锁释放前）
        Boolean b = lock.tryLock(1,10, TimeUnit.SECONDS);
        if(!b)
        {
            return Result.fail("不允许重复下单");
        }
        try{
            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            lock.unlock();
        }


    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        // 查重：该用户是否已购买过这张优惠券（user_id + voucher_id 组合）
        Long count = Long.valueOf(voucherOrderMapper.selectCount(
                new QueryWrapper<VoucherOrder>()
                        .eq("user_id", userId)
                        .eq("voucher_id", voucherId)));
        if (count > 0) {
            return Result.fail("您已经购买过该优惠券");
        }
        boolean voucherId1 = seckillVoucherService.update().setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if(!voucherId1){
            return Result.fail("服务繁忙！");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long order = redisIdWorker.nextId("order");
        voucherOrder.setId(order);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(order);
    }
}
