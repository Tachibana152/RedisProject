package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Object queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogTopLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);
}
