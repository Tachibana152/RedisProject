package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private BlogMapper blogMapper;
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IBlogService blogService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(records);
        }
        // 批量查询用户，避免循环内逐个查库（N+1 问题）
        Set<Long> userIds = records.stream().map(Blog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        // 填充点赞状态与用户信息
        records.forEach(blog -> {
            isBlogLiked(blog);
            User user = userMap.get(blog.getUserId());
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }
        });
        return Result.ok(records);
    }

    @Override
    public Object queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("不存在该blog");
        }
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isBlogLiked(blog);
        // 返回实体，由 Controller 层统一用 Result.ok 包装，避免双重包装导致前端解析失败
        return blog;
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        // 未登录（/blog/hot 是放行接口）时无法判断点赞状态，默认为 false，避免空指针
        if (user == null) {
            blog.setIsLike(false);
            return;
        }
        String key = "blog_" + blog.getId();
        // Spring Data Redis 2.7.x 的 ZSetOperations 没有 isMember，用 score() 判断成员是否存在（等价于 ZSCORE）
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "blog_"+id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){
            // 未点赞，执行点赞操作
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }
        else {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogTopLikes(Long id) {
        String key = "blog_" + id;
        // range 为升序（ZRANGE），score=点赞时间戳，越早点赞 score 越小排越前，取最早点赞的前5名
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> topLikes = new ArrayList<>();
        for (String userIdStr : top5) {
            User user = userService.getById(Long.valueOf(userIdStr));
            if (user != null) {
                UserDTO userDTO = new UserDTO();
                userDTO.setId(user.getId());
                userDTO.setNickName(user.getNickName());
                topLikes.add(userDTO);
            }
        }
        return Result.ok(topLikes);
    }
}
