package com.aiqa.project1.utils;

import com.aiqa.project1.pojo.User;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class UserUtils {
    public static Map<String, Object> User2Map(User user) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("username", user.getUsername());
        map.put("password", user.getPassword());
        map.put("role", user.getRole());
        return map;
    }

    public static Map<String, Object> Class2Map(Object user) {
        Map<String, Object> map = new HashMap<String, Object>();
        Class c = user.getClass();
        Field[] fields = c.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                if (field.get(user) != null) {
                    map.put(field.getName(), field.get(user));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                field.setAccessible(false);
            }
        }
        return map;
    }

    /**
     * 对象A和对象B有重复的字段，将A中的重复字段放到B中
     * @param A
     * @param B
     * @return
     * @param <T>
     */
    public static <T> T copyDuplicateFieldsFromA2B(Object A, T B) {
        Class<?> clazzA  = A.getClass();
        Class<?> clazzB = B.getClass();
        Field[] aFields = clazzA.getDeclaredFields();
        Field bField = null;
        for (Field aField : aFields) {
            aField.setAccessible(true);
            String fieldName = aField.getName();
            Class<?> fieldType = aField.getType();
            try {
                bField = findFieldInClassHierarchy(clazzB, fieldName, fieldType);
                if (bField != null) {
                    bField.setAccessible(true);
                    Object value = aField.get(A);
                    bField.set(B, value);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                aField.setAccessible(false);
                if (bField != null)
                    bField.setAccessible(false);
            }
        }
        return B;
    }

    private static Field findFieldInClassHierarchy(Class<?> clazz, String fieldName, Class<?> fieldType) {
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                if (field.getType().equals(fieldType)) {
                    return field; // 找到同名同类型字段，返回
                }
            } catch (NoSuchFieldException e) {
                // 当前类没有，向上遍历父类
                clazz = clazz.getSuperclass();
            }
        }
        return null; // 未找到
    }
}
