package com.tongcheng.service;

import com.tongcheng.dto.Result;
import com.tongcheng.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
public interface IFollowService extends IService<Follow> {

    Result Follow(Long id, Boolean isFollow);

    Result isFollow(Long id);

    Result followCommons(Long id);
}
