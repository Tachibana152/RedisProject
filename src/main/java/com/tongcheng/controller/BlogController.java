package com.tongcheng.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongcheng.dto.Result;
import com.tongcheng.dto.UserDTO;
import com.tongcheng.entity.Blog;
import com.tongcheng.entity.User;
import com.tongcheng.service.IBlogService;
import com.tongcheng.service.IUserService;
import com.tongcheng.utils.SystemConstants;
import com.tongcheng.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author Tachibana
 * @since 2026-08-05
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }
//
//    @PutMapping("/like/{id}")
//    public Result likeBlog(@PathVariable("id") Long id) {
//        // 修改点赞数量
//        blogService.update()
//                .setSql("liked = liked + 1").eq("id", id).update();
//        return Result.ok();
//    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return Result.ok(blogService.queryBlogById(id));
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }
    @GetMapping("/likes/{id}")
    public Result topLikes(@PathVariable("id")Long id)
    {

        return blogService.queryBlogTopLikes(id);
    }

    /**
     * 关注推送流（Feed流）：按发布时间倒序拉取关注用户的博客，游标分页
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,
                                    @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(max, offset);
    }
}
