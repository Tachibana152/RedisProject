package com.tongcheng.mapper;

import com.tongcheng.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
public interface FollowMapper extends BaseMapper<Follow> {
    @Select("select * from tb_follow where user_id = #{userId} and follow_user_id = #{id}")
    Follow selectFollow(Long userId, Long id);
}
