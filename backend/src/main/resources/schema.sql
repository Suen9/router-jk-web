CREATE DATABASE IF NOT EXISTS router_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE router_monitor;

CREATE TABLE IF NOT EXISTS router_session (
    id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(200) NOT NULL COMMENT '路由器SessionID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS supervision_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostname VARCHAR(100) COMMENT '设备名',
    mac VARCHAR(50) COMMENT 'MAC地址',
    time_slots VARCHAR(500) COMMENT '时间段JSON',
    single_duration INT COMMENT '单次使用时长(分钟)',
    daily_limit INT COMMENT '每日总时长(分钟)',
    used_today INT DEFAULT 0 COMMENT '今日已使用(秒)',
    session_used INT DEFAULT 0 COMMENT '当前会话已使用(秒)',
    extended_minutes INT DEFAULT 0 COMMENT '今日累计延长分钟数(午夜还原)',
    original_time_slots VARCHAR(500) COMMENT '延长前原始时间段JSON(午夜还原)',
    extend_active INT DEFAULT 0 COMMENT '延时模式 1=跳过监管巡视 0=正常',
    extend_expire_at DATETIME COMMENT '延时结束时间',
    blacklisted_by_supervision INT DEFAULT 0 COMMENT '是否被监管自动拉黑 0/1',
    status VARCHAR(20) DEFAULT 'active' COMMENT 'active/inactive',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS whitelist_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostname VARCHAR(100) COMMENT '设备名',
    mac VARCHAR(50) COMMENT 'MAC地址',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS connection_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name VARCHAR(100) COMMENT '设备名',
    mac VARCHAR(50) COMMENT 'MAC地址',
    connect_time DATETIME COMMENT '连接时间',
    disconnect_time DATETIME COMMENT '断开时间',
    duration INT COMMENT '单次时长(分钟)',
    connect_count INT COMMENT '连接次数',
    log_type VARCHAR(30) COMMENT 'normal/abnormal/timeout/blacklist/whitelist_violation',
    status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/processed/ignored',
    source VARCHAR(30) COMMENT 'supervision/whitelist/blacklist',
    remark VARCHAR(500) COMMENT '处理备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
