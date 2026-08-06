package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 批量登录测试：为 tb_user 表中所有用户生成 token 并写入 Redis
 * （与 UserServiceImpl.login 的 token 生成逻辑完全一致，前端可直接使用）
 * 同时导出 token 列表到 target/login-tokens.csv，供 Apifox 性能测试参数化使用
 */
@SpringBootTest
class UserLoginBatchTest {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void loginAllUsers() throws IOException {
        // 1. 查询全部用户
        List<User> users = userService.list();
        System.out.println("========== 共查询到 " + users.size() + " 个用户 ==========");

        // CSV 输出文件（位于 target 目录，不污染源码）
        String csvPath = Paths.get("target", "login-tokens.csv").toAbsolutePath().toString();
        PrintWriter csv = new PrintWriter(new FileWriter(csvPath));
        csv.println("userId,phone,nickName,token");

        int success = 0;
        for (User user : users) {
            // 2. 生成 token（与 login() 一致：不带横线的 UUID）
            String token = UUID.randomUUID().toString(true);

            // 3. 只保存登录需要的信息（id / nickName / icon）
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

            // 4. 写入 Redis：login:token:{token} 对应的 hash
            stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
            // 5. 设置有效期（与 login() 一致：36000 分钟）
            stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,
                    RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

            csv.println(user.getId() + "," + user.getPhone() + "," + user.getNickName() + "," + token);
            success++;
        }
        csv.close();

        System.out.println("========== 全部完成：" + success + "/" + users.size()
                + " 个用户已登录，token 已写入 Redis ==========");
        System.out.println("========== token 列表已导出到：" + csvPath + " ==========");
    }
}
