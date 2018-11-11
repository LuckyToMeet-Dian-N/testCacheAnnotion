package com.wen.demo.utils;
import com.alibaba.fastjson.JSON;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;

import java.lang.reflect.Method;
/**
 * @Description:
 * @Author: Gentle
 * @date 2018/10/14 17:07
 */
@Component
@Aspect
public class Authorizations {
    /**
     *  这个懒得写成一个类了。。就凑合这样写了。整合到自己的项目，可以删除，修改
     */
    Jedis jedis = new Jedis(setJedisShardInfo());

    public JedisShardInfo setJedisShardInfo(){
        JedisShardInfo jedisShardInfo = new JedisShardInfo("");
        jedisShardInfo.setPassword("");
        return jedisShardInfo;
    }


    /**
     * 正文开始是如下
     */
    private static final String Default_String = ":";

    @Around("@annotation(redisCacheAble)")
    public Object handlers(ProceedingJoinPoint joinPoint, RedisCacheAble redisCacheAble) {
        try {
            //拿到存入redis的键
            String handler = returnRedisKey(joinPoint, redisCacheAble.key(), redisCacheAble.cacheName());
            //查询redis，看有没有。有就直接返回。没有。就GG
            String  redisCacheValue = getRedisCacheValue(redisCacheAble, handler);
            if (redisCacheValue != null) {
                System.out.println("使用缓存" + redisCacheValue);
                //拿到返回值类型
                Class<?> methodReturnType = getMethodReturnType(joinPoint);
                //处理从redis拿出的字符串。
                Object o= JSON.parseObject(redisCacheValue,methodReturnType);
                System.out.println("o的值是："+o);
               return o;
            }

            //执行原来方法
            Object proceed = joinPoint.proceed();
            //放入缓存
            useRedisCache(redisCacheAble, handler, proceed);
            return proceed;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }

    /**
     *  切的方法。这样写是遇到这个注解的时候AOP来处理，为项目解耦是一方面
     * @param joinPoint
     * @param redisCacheEvict
     * @return
     */
    @Around("@annotation(redisCacheEvict)")
    public Object handlers(ProceedingJoinPoint joinPoint, RedisCacheEvict redisCacheEvict) {
        Object object=null;
        try {
            String handler = returnRedisKey(joinPoint, redisCacheEvict.key(), redisCacheEvict.cacheName());
            System.out.println("删除的键："+handler);
            if (redisCacheEvict.isHash()) {
                jedis.hdel(handler, redisCacheEvict.field());
            } else {
                jedis.del(handler);
            }
             object=joinPoint.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return object;
    }


    /**
     * 使用redis缓存，这个不该写在这的。。为了简单起见。就混入这了
     * @param redisCacheAble
     * @param redisKeyName
     * @param redisValue
     * @throws Exception
     */
    private void useRedisCache(RedisCacheAble redisCacheAble, String redisKeyName, Object redisValue) throws Exception {

        int time = redisCacheAble.time();

        if (redisCacheAble.isHash()) {

            if (time != -1) {
                System.out.println("插入哈希缓存（有时间）");
                String field = redisCacheAble.field();

                jedis.hset(redisKeyName, field, JSON.toJSONString(redisValue));
                jedis.expire(redisKeyName, time);
            } else {
                System.out.println("插入哈希缓存");
                String field = redisCacheAble.field();
                jedis.hset(redisKeyName, field, JSON.toJSONString(redisValue));
            }
        } else {
            if (time != -1) {
                System.out.println("插入缓存（有时间）");
                jedis.set(redisKeyName, JSON.toJSONString(redisValue));
                jedis.expire(redisKeyName, time);
            } else {
                System.out.println("插入缓存");
                jedis.set(redisKeyName,JSON.toJSONString(redisValue));
            }

        }
    }

    /**
     * 获取redis缓存的值，这个不该写在这的。。为了简单起见。就混入这了
     * @param redisCacheAble
     * @param object
     * @return
     * @throws Exception
     */
    private String  getRedisCacheValue(RedisCacheAble redisCacheAble, String object) throws Exception {

        if (redisCacheAble.isHash()) {
            String field = redisCacheAble.field();
            System.out.println(object + "  " + field);
            return jedis.hget(object, field);
        } else {
            return jedis.get(object);
        }
    }

    /**
     * 主要的任务是将生成的redis key返回
     * @param joinPoint
     * @param keys
     * @param cacheName
     * @return
     * @throws Exception
     */
    private String returnRedisKey(ProceedingJoinPoint joinPoint, String keys, String cacheName) throws Exception {

        boolean b = checkKey(keys);
        if (!b) {
            throw new RuntimeException("键规则有错误或键为空");
        }
        String key = getSubstringKey(keys);
        //判定是否有. 例如#user.id  有则要处理，无则进一步处理
        if (!key.contains(".")) {
            Object arg = getArg(joinPoint, key);
            //判定请求参数中是否有相关参数。无则直接当键处理，有则取值当键处理
            String string;
            if (arg == null) {
                string = handlerRedisKey(cacheName, key);
            } else {
                string = handlerRedisKey(cacheName, arg);
            }
            return string;

        } else {
            //拿到对象参数 例如  user.id  拿到的是user这个相关对象
            Object arg = getArg(joinPoint, handlerIncludSpot(key));
            Object objectKey = getObjectKey(arg, key.substring(key.indexOf(".") + 1));
            return handlerRedisKey(cacheName, objectKey);
        }
    }


    private String handlerRedisKey(String cacheName, Object key) {
        return cacheName + Default_String + key;
    }

    /**
     * 递归找到相关的参数，并最终返回一个值
     *
     * @param object 传入的对象
     * @param key    key名,用于拼接成 get+key
     * @return 返回处理后拿到的值  比如 user.id  id的值是10  则将10返回
     * @throws Exception 异常
     */
    private Object getObjectKey(Object object, String key) throws Exception {
        //判断key是否为空
        if (StringUtils.isEmpty(key)) {
            return object;
        }
        //拿到user.xxx  例如：key是user.user.id  递归取到最后的id。并返回数值
        int doIndex = key.indexOf(".");
        if (doIndex > 0) {
            String propertyName = key.substring(0, doIndex);
            //截取
            key = key.substring(doIndex + 1);
            Object obj = getProperty(object, getMethod(propertyName));
            return getObjectKey(obj, key);
        }
        return getProperty(object, getMethod(key));
    }

    /**
     * 也是截取字符串。没好说的
     *
     * @param key 传入的key
     */
    private String handlerIncludSpot(String key) {
        int doIndex = key.indexOf(".");
        return key.substring(0, doIndex);
    }

    /**
     * 获取某方法中的返回值。。例如：public int getXXX()  拿到的是返回int的的数值
     *
     * @param object     对象实例
     * @param methodName 方法名
     * @return 返回通过getXXX拿到属性值
     * @throws Exception 异常
     */
    private Object getProperty(Object object, String methodName) throws Exception {
        return object.getClass().getMethod(methodName).invoke(object);
    }

    /**
     * 返回截取的的字符串
     *
     * @param keys 用于截取的键
     * @return 返回截取的的字符串
     */
    private String getSubstringKey(String keys) {
        //去掉# ，在设置例如 #user 变成 user
        return keys.substring(1).substring(0, 1) + keys.substring(2);
    }

    /**
     * 获得get方法，例如拿到了User对象，拿他的setXX方法
     *
     * @param key 键名，用于拼接
     * @return 方法名字（即getXXX() ）
     */
    private String getMethod(String key) throws Exception {

        return "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    /**
     * 获取请求的参数。
     *
     * @param joinPoint 切点
     * @param paramName 请求参数的名字
     * @return 返回和参数名一样的参数对象或值
     * @throws NoSuchMethodException 异常
     */
    private Object getArg(ProceedingJoinPoint joinPoint, String paramName) throws NoSuchMethodException {
        Signature signature = joinPoint.getSignature();

        //获取请求的参数
        MethodSignature si = (MethodSignature) signature;
        Method method0 = joinPoint.getTarget().getClass().getMethod(si.getName(), si.getParameterTypes());
        ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
        String[] p = parameterNameDiscoverer.getParameterNames(method0);
        if (p == null) {
            throw new IllegalArgumentException("没有参数[" + paramName + "] 没有方法:" + method0);
        }
        //判断是否有相关参数
        int indix = 0;

        for (String string : p) {
            if (string.equalsIgnoreCase(paramName)) {
                return joinPoint.getArgs()[indix];
            }
            indix++;
        }
        return null;
    }

    /**
     * 键规则检验 是否符合开头#
     *
     * @param key 传入的key
     * @return 返回是否包含
     */
    private boolean checkKey(String key) {
        if (StringUtils.isEmpty(key)) {
            return false;
        }
        String temp = key.substring(0, 1);
        //如果没有以#开头，报错
        return temp.equals("#");
    }


    /**
     * 方法不支持返回值是集合类型  例如List<User> 无法获取集合中的对象。
     * 支持对象，基本类型，引用类型
     * @param joinPoint
     * @return
     */
  private Class<?> getMethodReturnType(ProceedingJoinPoint joinPoint){

      Signature signature = joinPoint.getSignature();
      Class declaringType = signature.getDeclaringType();
      String name = signature.getName();

      Method[] methods = declaringType.getMethods();
      for (Method method :methods){
          if (method.getName().equals(name)){
              Class<?> returnType = method.getReturnType();
              System.out.println("返回值类型："+returnType);
              return  returnType;
          }
      }
     throw  new RuntimeException("找不到返回参数。请检查方法返回值是否是对象类型");
  }
}
