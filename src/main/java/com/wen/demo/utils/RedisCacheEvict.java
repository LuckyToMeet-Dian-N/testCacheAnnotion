package com.wen.demo.utils;

import java.lang.annotation.*;

/**
 * @Description:
 * @Author: Gentle
 * @date 2018/10/14  17:04
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisCacheEvict {
    //字段名，用于存哈希字段，该字段要isHash()为true的时候才能用
    String field() default "field" ;
    //缓存的名字，配合一下的key一起使用
    String cacheName() default "cacheName" ;
    //key，传入的对象，例如写的是#id  id=1  键一定要写#
    //生成的redis键为  cacheName:1
    String key()  ;
    //判断是否使用哈希类型
    boolean isHash() default false;

}
