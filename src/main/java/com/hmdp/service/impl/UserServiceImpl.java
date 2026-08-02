package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone))
        {
            return Result.fail("手机号格式错误");
        }
        String s = RandomUtil.randomNumbers(6);

        session.setAttribute("code",s);
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
        if(!loginForm.getCode().equals(session.getAttribute("code").toString()))
        {
            return Result.fail("验证码错误");
        }
        if(loginForm.getCode().equals(session.getAttribute("code").toString()))
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
            session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
            return Result.ok();
        }
        return null;
    }
}
