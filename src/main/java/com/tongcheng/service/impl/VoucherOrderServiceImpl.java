package com.tongcheng.service.impl;

import com.tongcheng.dto.Result;
import com.tongcheng.entity.SeckillVoucher;
import com.tongcheng.entity.VoucherOrder;
import com.tongcheng.mapper.VoucherOrderMapper;
import com.tongcheng.service.ISeckillVoucherService;
import com.tongcheng.service.IVoucherOrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.bean.BeanUtil;
import com.tongcheng.utils.RedisIdWorker;
import com.tongcheng.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    // 消息队列名称：stream.orders（Redis Stream 结构）
    private static final String STREAM_NAME = "stream.orders";
    // 消费者组与消费者名称
    private static final String GROUP_NAME = "g1";
    private static final String CONSUMER_NAME = "c1";

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        // 创建消费者组（MKSTREAM：stream 不存在时自动创建）
        try {
            stringRedisTemplate.execute((RedisCallback<Object>) connection ->
                    // 注意：2.3.9 版本签名是 xGroupCreate(byte[] key, String groupName, ReadOffset, boolean)
                    connection.xGroupCreate(
                            STREAM_NAME.getBytes(),
                            GROUP_NAME,
                            ReadOffset.from("0"),
                            true));
        } catch (Exception e) {
            log.info("消费者组已存在，无需创建");
        }
        // 启动后台线程，持续从 stream.orders 中获取消息并完成下单
        executorService.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 从 stream.orders 的消费者组中读取一条新消息（阻塞 2 秒）
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_NAME, ReadOffset.lastConsumed())
                    );
                    // 2. 消息为空则继续轮询
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 3. 解析消息为订单对象（包含 voucherId、userId、orderId）
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 4. 完成下单
                    handleVoucherOrder(voucherOrder);
                    // 5. ACK 确认消息已处理
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_NAME, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("处理 stream 订单消息异常", e);
                    // 处理 pending-list 中的异常消息
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1. 读取 pending-list 中的消息（未被 ACK 的消息，从 0 开始）
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(STREAM_NAME, ReadOffset.from("0"))
                    );
                    // 2. pending-list 为空，结束处理
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    // 3. 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 4. 完成下单
                    handleVoucherOrder(voucherOrder);
                    // 5. ACK 确认
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_NAME, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("处理 pending 订单消息异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
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

        // 2. 生成订单 id（写入 stream.orders 消息，由后台线程异步落库）
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 3. 使用 Lua 脚本进行 Redis 操作：校验 + 扣库存 + 发送消息到 stream.orders
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                Long.toString(now),
                Long.toString(begin),
                Long.toString(end),
                Long.toString(orderId));
        if (result == null) {
            return Result.fail("秒杀失败，请重试");
        }
        int i = result.intValue();
        if (i != 0) {
            return Result.fail(i == 1 ? "库存不足" : (i == 2 ? "您已经购买过该商品" : "秒杀尚未开始或已经结束"));
        }

        // 4. 抢购成功：返回订单 id（订单由后台线程从 stream.orders 消费后落库）
        return Result.ok(orderId);
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