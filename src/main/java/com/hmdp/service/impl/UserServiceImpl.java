package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone))
        {
            return Result.fail("手机号格式错误");
        }
        String s = RandomUtil.randomNumbers(6);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, s, LOGIN_CODE_TTL, TimeUnit.MINUTES);

//        session.setAttribute("code",s);
        log.debug("发送短信验证码成功：{}",s);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone()))
        {
            return Result.fail("手机号格式错误");
        }
        if(loginForm.getCode() == null || loginForm.getCode().length() != 6)
        {
            return Result.fail("验证码格式错误");
        }
        String RedisCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if(!loginForm.getCode().equals(RedisCode))
        {
            return Result.fail("验证码错误");
        }
        if(loginForm.getCode().equals(RedisCode))
        {
            User user = query().eq("phone", loginForm.getPhone()).one();
            if(user==null)
            {
                user = new User();
                user.setPhone(loginForm.getPhone());
                user.setNickName("Airi_" + RandomUtil.randomString(6));
                user.setCreateTime(LocalDateTime.now());
                user.setUpdateTime(LocalDateTime.now());
                save(user);
            }
        String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = new UserDTO();
            userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
            stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, stringObjectMap );
            stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);


            //session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
            return Result.ok(token);
        }
        return null;
    }

    @Override
    public Result logout(String token) {
        if (token == null || token.isEmpty()) {
            return Result.fail("未登录");
        }
        // 删除 Redis 中的登录态，下次请求拦截器就查不到用户了
        Boolean deleted = stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        if (Boolean.TRUE.equals(deleted)) {
            return Result.ok();
        }
        return Result.fail("退出失败");
    }

    /**
     * 实现签到功能
     * @return
     */
    @Override
    public Result sign() {
        UserDTO user = UserHolder.getUser();
        if(user == null)
        {
            return Result.fail("未登录");
        }
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + user.getId() + format;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        UserDTO user = UserHolder.getUser();
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + user.getId() + format;
        int dayOfMonth = now.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0)
        );
        if(result == null || result.isEmpty())
        {
            return Result.ok(0);
        }
        Long l1 = result.get(0);
        if(l1==null || l1==0)
        {
            return Result.ok(0);
        }
        int count = 0;
        while (true)
        {
            if((l1 & 1) == 0)
            {
                break;
            }else {
                count ++;
            }
            l1 >>>= 1;
        }
        return Result.ok(count);
    }
}
