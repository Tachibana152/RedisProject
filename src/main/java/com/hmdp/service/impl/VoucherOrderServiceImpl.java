package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
@Service
@Slf4j
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

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>();

    static {
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        executorService.submit(new VoucherOrderHandler());
    }
        private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
        Long userId = voucherOrder.getUserId();
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 一人一单：对同一用户加锁，锁内调用事务方法（事务提交发生在方法返回前，即锁释放前）
        Boolean b = lock.tryLock(1,10, TimeUnit.SECONDS);
        if(!b)
        {
            log.info("不允许重复下单");
            return ;
        }
        try{
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result secKillVoucher(Long voucherId) throws InterruptedException {
        // 1. 查秒杀券信息，获取开始/结束时间（时间校验交给 Lua 原子执行）
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("秒杀券不存在");
        }
        long now = System.currentTimeMillis();
        long begin = seckillVoucher.getBeginTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = seckillVoucher.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // 2. 使用 Lua 脚本进行 Redis 操作，同时根据返回的值进行判断
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                UserHolder.getUser().getId().toString(),
                Long.toString(now),
                Long.toString(begin),
                Long.toString(end));
        if (result == null) {
            return Result.fail("秒杀失败，请重试");
        }
        int i = result.intValue();
        if (i != 0) {
            return Result.fail(i == 1 ? "库存不足" : (i == 2 ? "您已经购买过该商品" : "秒杀尚未开始或已经结束"));
        }

        // 3. 秒杀成功：生成订单 id，放入异步队列，由后台线程落库
        VoucherOrder voucherOrder = new VoucherOrder();
        long order = redisIdWorker.nextId("order");
        voucherOrder.setId(order);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);

        return Result.ok(order);
    }


    //    @Override
//    public Result secKillVoucher(Long voucherId) throws InterruptedException {
//        //check
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//
//        //judge seckill begin or end
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//        if(LocalDateTime.now().isBefore(beginTime) || LocalDateTime.now().isAfter(endTime)){
//            return Result.fail("秒杀未开始或已结束");
//        }
//        if(seckillVoucher.getStock()<1)
//        {
//            return Result.fail("库存不足!");
//        }
//        UserDTO user = UserHolder.getUser();
//        Long userId = user.getId();
//        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 一人一单：对同一用户加锁，锁内调用事务方法（事务提交发生在方法返回前，即锁释放前）
//        Boolean b = lock.tryLock(1,10, TimeUnit.SECONDS);
//        if(!b)
//        {
//            return Result.fail("不允许重复下单");
//        }
//        try{
//            return proxy.createVoucherOrder(voucherId, userId);
//        } finally {
//            lock.unlock();
//        }
//
//    }
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 查重：该用户是否已购买过这张优惠券（user_id + voucher_id 组合）
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Long count = Long.valueOf(voucherOrderMapper.selectCount(
                new QueryWrapper<VoucherOrder>()
                        .eq("user_id", userId)
                        .eq("voucher_id", voucherId)));
        if (count > 0) {
            return ;
        }
        boolean voucherId1 = seckillVoucherService.update().setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if(!voucherId1){
            return ;
        }
        save(voucherOrder);
    }
}
