package com.serene.tube.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class A {
    public static JavaType getJavaType(Type type) {
        //判断是否是参数化类型，即泛型
        if (type instanceof ParameterizedType) {
            Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
            //获取泛型类型
            Class rowClass = (Class) ((ParameterizedType) type).getRawType();
            JavaType[] javaTypes = new JavaType[actualTypeArguments.length];
            for (int i = 0; i < actualTypeArguments.length; i++) {
                //泛型也可能带有泛型，递归获取
                javaTypes[i] = getJavaType(actualTypeArguments[i]);
            }
            return TypeFactory.defaultInstance().constructParametricType(rowClass, javaTypes);
        } else {
            //简单类型直接用该类构建JavaType
            Class cla = (Class) type;
            try {
                return TypeFactory.defaultInstance().constructParametricType(cla, new JavaType[0]);
            } catch (IllegalArgumentException e) {
                return TypeFactory.defaultInstance().constructParametricType(cla, Object.class);
            }
        }
    }
}
