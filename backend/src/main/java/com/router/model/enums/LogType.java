package com.router.model.enums;

import lombok.Getter;

@Getter
public enum LogType {
    NORMAL("normal", "普通连接"),
    ABNORMAL("abnormal", "异常连接"),
    TIMEOUT("timeout", "超时告警"),
    BLACKLIST("blacklist", "黑名单触发"),
    WHITELIST_VIOLATION("whitelist_violation", "白名单外设备");

    private final String code;
    private final String desc;

    LogType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
