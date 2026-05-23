package com.ticket.common.result;

import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一接口响应封装类.
 *
 * 所有 REST 接口返回此对象,包含 code(状态码)、message(描述信息)、data(业务数据)、traceId(链路追踪号).
 * traceId 由 TraceIdFilter 在请求入口注入到 MDC,Result 在构造时自动读取,便于客户端在排查问题时给出来.
 */
public class Result<T> implements Serializable {

    /** MDC 中 traceId 的 key（与 TraceIdFilter / logback pattern 保持一致） */
    public static final String TRACE_ID_KEY = "traceId";

    private int code;
    private String message;
    private T data;
    /** 链路追踪号；可能为 null（如非 Web 上下文调用） */
    private String traceId;

    private Result() {
        this.traceId = MDC.get(TRACE_ID_KEY);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
