package com.wen.demo.utils;

import org.springframework.stereotype.Component;
/**
 * @Description:
 * @Author: Gentle
 * @date 2018/10/14  17:23
 */
@Component
public class MyTest {

    @RedisCacheAble(key="#users.id",cacheName = "wen")
    public Users test(Users users){
        users.setWen("wen");
        return users;
    }

    @RedisCacheAble(key="#id",cacheName = "wen")
    public int test(int id){

        return 100;
    }
    @RedisCacheEvict(key = "#id",cacheName = "wen")
    public int  abc(int id){

        return 100;
    }

}
