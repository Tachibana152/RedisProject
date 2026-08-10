package com.tongcheng;

import com.tongcheng.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class TongChengApplicationTests {
@Resource
    ShopServiceImpl shopService;
//@Test
//    void testShopService() {
//shopService.saveShop2Redis(1L,10L);
//}

}
