package com.guild.checkout.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 全站统一响应结构。成功与失败使用同一外层结构，客户端只需判断 success。
 *
 * @param success 是否成功
 * @param code    业务码：成功固定为 OK，失败为 {@link ErrorCode#code()}
 * @param message 提示文案，可直接展示给用户
 * @param data    成功时的业务数据，失败时为 null
 * @param errors  参数校验失败时的逐字段明细，其余情况为 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        List<FieldError> errors) {

    public static final String OK = "OK";

    /** 参数校验的逐字段错误。 */
    public record FieldError(String field, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, OK, "成功", data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, OK, "成功", null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.code(), message, null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, List<FieldError> errors) {
        return new ApiResponse<>(false, errorCode.code(), message, null, errors);
    }
}
