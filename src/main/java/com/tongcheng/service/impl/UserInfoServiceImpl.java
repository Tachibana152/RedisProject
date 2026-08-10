package com.tongcheng.service.impl;

import com.tongcheng.entity.UserInfo;
import com.tongcheng.mapper.UserInfoMapper;
import com.tongcheng.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
