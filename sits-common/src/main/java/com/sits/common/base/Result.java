package com.sits.common.base;

import java.io.Serializable;

/**
 * 统一 API 响应包装类，用于规范所有接口的返回格式。
 *
 * <p>泛型参数 T 表示响应数据的具体类型。所有静态工厂方法返回不可变的 Result 实例。
 *
 * @param <T> 响应数据的类型
 */
public class Result<T> implements Serializable {

    /** HTTP 状态码 */
    private int code;
    /** 响应消息 */
    private String message;
    /** 响应数据 */
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应（无数据），状态码 200。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /**
     * 创建成功响应（带数据），状态码 200。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 创建成功响应（自定义消息和数据），状态码 200。
     *
     * @param message 自定义成功消息
     * @param data    响应数据
     * @param <T>     数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /**
     * 创建失败响应（自定义状态码和消息）。
     *
     * @param code    业务错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 创建失败响应（默认状态码 500）。
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }

    /**
     * 判断当前响应是否为成功状态（状态码 == 200）。
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return code == 200;
    }
}
