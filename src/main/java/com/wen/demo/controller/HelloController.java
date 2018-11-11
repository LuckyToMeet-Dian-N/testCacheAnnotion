package com.wen.demo.controller;

import com.wen.demo.utils.MyTest;
import com.wen.demo.utils.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.InvocationTargetException;

/**
 * @Description:
 * @Author: Gentle
 * @date 2018/10/26  20:31
 */
@RestController
public class HelloController {
    @Autowired
    MyTest myTest;

    @RequestMapping(value = "hello")
    public Users test() throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Users users = new Users();
        users.setId(20);
        users.setWen("wen");
        return myTest.test(users);
    }

    @RequestMapping(value = "delete")
    public int delete() throws IllegalAccessException, InstantiationException, InvocationTargetException {
        return myTest.abc(20);
    }


}
