package com.tongcheng.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tongcheng.dto.LoginFormDTO;
import com.tongcheng.dto.Result;
import com.tongcheng.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result logout(String token);

    Result sign();

    Result signCount();
}
