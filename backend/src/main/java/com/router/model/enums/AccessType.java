package com.router.model.enums;

import lombok.Getter;

@Getter
public enum AccessType {
    DENY(0, "不允许接入"),
    ALLOW_NO_INTERNET(1, "允许接入但禁上网"),
    ALLOW_INTERNET(2, "允许接入并上网");

    private final int code;
    private final String desc;

    AccessType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AccessType fromCode(int code) {
        for (AccessType t : values()) {
            if (t.code == code) return t;
        }
        return ALLOW_INTERNET;
    }
}
