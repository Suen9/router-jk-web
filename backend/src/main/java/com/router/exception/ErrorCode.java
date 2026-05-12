package com.router.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_FOUND(40400, "请求数据不存在"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    ROUTER_ERROR(60000, "路由器通信异常"),
    ROUTER_AUTH_REQUIRED(60001, "路由器授权过期"),
    ROUTER_AUTH_FAILED(60002, "路由器登录失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
