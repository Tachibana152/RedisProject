package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

public class LoginInterceptor implements HandlerInterceptor {



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
////        HttpSession session = request.getSession();
//        String token = request.getHeader("authorization");
//        if(token == null){
//            response.setStatus(401);
//            return false;
//        }
//        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
//
////        Object user = session.getAttribute("user");
//        if(entries.isEmpty())
//        {
//            response.setStatus(401);
//
//            return false;
//        }
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
//        UserHolder.saveUser((userDTO));
//        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL , TimeUnit.MINUTES);

        if(UserHolder.getUser() == null)
        {
            response.setStatus(401);
            return false;
        }
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
       UserHolder.removeUser();
    }
}
