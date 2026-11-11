package com.guild.checkout.common.web;

import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.api.ErrorCode;
import com.guild.checkout.common.exception.AppException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理。所有异常一律转为 {@link ApiResponse} 结构，
 * HTTP 状态码取自 {@link ErrorCode}，不向客户端泄露堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：未认证、无权限、业务拒绝、资源不存在等。 */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        ErrorCode ec = ex.errorCode();
        if (ec.status().is5xxServerError()) {
            log.error("业务异常[{}]", ec.code(), ex);
        } else {
            log.warn("业务异常[{}]: {}", ec.code(), ex.getMessage());
        }
        return ResponseEntity.status(ec.status()).body(ApiResponse.fail(ec, ex.getMessage()));
    }

    /** 请求体参数校验失败（@RequestBody + @Valid）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyInvalid(MethodArgumentNotValidException ex) {
        List<ApiResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return invalidParam(errors);
    }

    /** 表单或查询参数绑定校验失败。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        List<ApiResponse.FieldError> errors = ex.getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return invalidParam(errors);
    }

    /** 方法参数上的约束校验失败（@Validated + @RequestParam）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<ApiResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        return invalidParam(errors);
    }

    /** 必填参数缺失。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return invalidParam(List.of(new ApiResponse.FieldError(ex.getParameterName(), "参数缺失")));
    }

    /** 参数类型不匹配。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return invalidParam(List.of(new ApiResponse.FieldError(ex.getName(), "参数格式不正确")));
    }

    /** 请求体无法解析。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return invalidParam(List.of(new ApiResponse.FieldError("body", "请求体格式不正确")));
    }

    /** 路由不存在：必须是 404，不能被兜底处理器吞成 500。 */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(Exception ex) {
        log.warn("路由不存在: {}", ex.getMessage());
        ErrorCode ec = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(ec.status()).body(ApiResponse.fail(ec, ec.defaultMessage()));
    }

    /** 兜底：未预期的异常一律记录完整堆栈，对外只给通用文案。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        log.error("未预期异常", ex);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.status()).body(ApiResponse.fail(ec, ec.defaultMessage()));
    }

    private static ResponseEntity<ApiResponse<Void>> invalidParam(List<ApiResponse.FieldError> errors) {
        ErrorCode ec = ErrorCode.INVALID_PARAM;
        return ResponseEntity.status(ec.status())
                .body(ApiResponse.fail(ec, ec.defaultMessage(), errors));
    }

    private static ApiResponse.FieldError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return new ApiResponse.FieldError(dot >= 0 ? path.substring(dot + 1) : path, violation.getMessage());
    }
}
