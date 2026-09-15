package com.ctrpc.rpc.serialize;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.ctrpc.rpc.exception.RpcException;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/** Fastjson2 implementation of the RPC payload codec. */
public final class Fastjson2RpcCodec implements RpcCodec {
    private static final JSONWriter.Feature[] WRITE_FEATURES = { JSONWriter.Feature.WriteEnumsUsingName };
    private static final JSONReader.Feature[] READ_FEATURES = {
            JSONReader.Feature.SupportSmartMatch,
            JSONReader.Feature.UseNativeObject
    };

    @Override
    public String encodeArgs(Object[] args) {
        try {
            return JSON.toJSONString(args == null ? new Object[0] : args, WRITE_FEATURES);
        } catch (Exception e) {
            throw new RpcException(500, "serialize args failed", e);
        }
    }

    @Override
    public Object[] decodeArgs(String payload, Class<?>[] parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) return new Object[0];
        try {
            JSONArray array = JSON.parseArray(StringUtils.hasText(payload) ? payload : "[]");
            if (array == null) array = new JSONArray();
            if (array.size() != parameterTypes.length) {
                throw new RpcException(400, "args length mismatch, expect=" + parameterTypes.length + " actual=" + array.size());
            }
            Object[] result = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) result[i] = array.getObject(i, parameterTypes[i]);
            return result;
        } catch (RpcException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcException(400, "deserialize args failed", e);
        }
    }

    @Override
    public String encodeResult(Object result) {
        if (result == null) return "";
        try {
            return JSON.toJSONString(result, WRITE_FEATURES);
        } catch (Exception e) {
            throw new RpcException(500, "serialize result failed", e);
        }
    }

    @Override
    public Object decodeResult(String payload, Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType == Void.TYPE || returnType == Void.class) return null;
        if (!StringUtils.hasText(payload) || "null".equals(payload)) return null;
        try {
            return JSON.parseObject(payload, returnType, READ_FEATURES);
        } catch (Exception e) {
            throw new RpcException(500, "deserialize result failed", e);
        }
    }

    public static String parameterTypesKey(Class<?>[] parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(parameterTypes[i].getName());
        }
        return sb.toString();
    }

    public static Class<?>[] parseParameterTypes(String parameterTypes) {
        if (!StringUtils.hasText(parameterTypes)) return new Class<?>[0];
        String[] parts = parameterTypes.split(",");
        Class<?>[] types = new Class<?>[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) types[i] = loadClass(parts[i].trim());
            return types;
        } catch (ClassNotFoundException e) {
            throw new RpcException(400, "unknown parameter type: " + e.getMessage(), e);
        }
    }

    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name.equals("boolean")) return boolean.class;
        if (name.equals("byte")) return byte.class;
        if (name.equals("char")) return char.class;
        if (name.equals("short")) return short.class;
        if (name.equals("int")) return int.class;
        if (name.equals("long")) return long.class;
        if (name.equals("float")) return float.class;
        if (name.equals("double")) return double.class;
        if (name.equals("void")) return void.class;
        return Class.forName(name);
    }
}
