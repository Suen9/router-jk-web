package com.router.model.dto;

import lombok.Data;

@Data
public class BlacklistItem {
    private String mac;
    private String name;
    private String addTime;
    private String source;
    private String remark;
    private String status;
}
