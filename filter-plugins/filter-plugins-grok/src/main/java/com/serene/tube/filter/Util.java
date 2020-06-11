package com.serene.tube.filter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class Util {
    private static Method namedGroups;

    static {
        try {
            namedGroups = Pattern.class.getDeclaredMethod("namedGroups");
            namedGroups.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Integer> namedGroups(Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        try {
            return (Map<String, Integer>) namedGroups.invoke(pattern);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
