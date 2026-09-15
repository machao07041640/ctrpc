package com.ctrpc.rpc.serialize;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.ctrpc.rpc.exception.RpcException;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * RPC 参数/返回值序列化，基于 Fastjson2。
 */
public final class RpcSerializer {

    private static final JSONWriter.Feature[] WRITE_FEATURES = {
            JSONWriter.Feature.WriteEnumsUsingName
    };

    private static final JSONReader.Feature[] READ_FEATURES = {
            JSONReader.Feature.SupportSmartMatch,
            JSONReader.Feature.UseNativeObject
    };

    public String writeArgs(Object[] args) {
        try {
            return JSON.toJSONString(args == null ? new Object[0] : args, WRITE_FEATURES);
        } catch (Exception e) {
            throw new RpcException(500, "serialize args failed: " + e.getMessage(), e);
        }
    }

    public Object[] readArgs(String argsJson, Class<?>[] parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) {
            return new Object[0];
        }
        try {
            JSONArray array = JSON.parseArray(argsJson == null || StringUtils.isEmpty(argsJson) ? "[]" : argsJson);
            if (array == null) {
                array = new JSONArray();
            }
            if (array.size() != parameterTypes.length) {
                throw new RpcException(400,
                        "args length mismatch, expect=" + parameterTypes.length + " actual=" + array.size());
            }
            Object[] result = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                result[i] = array.getObject(i, parameterTypes[i]);
            }
            return result;
        } catch (RpcException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcException(400, "deserialize args failed: " + e.getMessage(), e);
        }
    }

    public String writeResult(Object result) {
        if (result == null) {
            return "";
        }
        try {
            return JSON.toJSONString(result, WRITE_FEATURES);
        } catch (Exception e) {
            throw new RpcException(500, "serialize result failed: " + e.getMessage(), e);
        }
    }

    public Object readResult(String dataJson, Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType == Void.TYPE || returnType == Void.class) {
            return null;
        }
        if (dataJson == null || StringUtils.isEmpty(dataJson) || "null".equals(dataJson)) {
            return null;
        }
        try {
            return JSON.parseObject(dataJson, returnType, READ_FEATURES);
        } catch (Exception e) {
            throw new RpcException(500, "deserialize result failed: " + e.getMessage(), e);
        }
    }

    public static String parameterTypesKey(Class<?>[] parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parameterTypes[i].getName());
        }
        return sb.toString();
    }

    public static Class<?>[] parseParameterTypes(String parameterTypes) {
        if (parameterTypes == null || StringUtils.isEmpty(parameterTypes)) {
            return new Class<?>[0];
        }
        String[] parts = parameterTypes.split(",");
        Class<?>[] types = new Class<?>[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                types[i] = loadClass(parts[i].trim());
            }
            return types;
        } catch (ClassNotFoundException e) {
            throw new RpcException(400, "unknown parameter type: " + e.getMessage(), e);
        }
    }

    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name.equals("boolean")) {
            return boolean.class;
        } else if (name.equals("byte")) {
            return byte.class;
        } else if (name.equals("char")) {
            return char.class;
        } else if (name.equals("short")) {
            return short.class;
        } else if (name.equals("int")) {
            return int.class;
        } else if (name.equals("long")) {
            return long.class;
        } else if (name.equals("float")) {
            return float.class;
        } else if (name.equals("double")) {
            return double.class;
        } else if (name.equals("void")) {
            return void.class;
        } else {
            return Class.forName(name);
        }
    }
}
