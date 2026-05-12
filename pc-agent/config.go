package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

// Config 应用配置 — 保存后端地址和 Agent 标识
type Config struct {
	BackendURL string `json:"backend_url"` // 后端地址，如 http://192.168.10.122:9070
	AgentKey   string `json:"agent_key"`   // 预共享密钥（可选，用于注册时鉴权）
}

// loadConfig 从 config.json 加载配置
func loadConfig(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败: %w", err)
	}
	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}
	if cfg.BackendURL == "" {
		return nil, fmt.Errorf("backend_url 不能为空")
	}
	return &cfg, nil
}

// TokenStore 本地 Token 存储 — 保存注册后获取的 agentToken
type TokenStore struct {
	DeviceID   uint64 `json:"device_id"`
	AgentToken string `json:"agent_token"`
}

// loadToken 从本地文件加载已保存的 token
func loadToken(path string) (*TokenStore, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取token文件失败: %w", err)
	}
	var ts TokenStore
	if err := json.Unmarshal(data, &ts); err != nil {
		return nil, fmt.Errorf("解析token文件失败: %w", err)
	}
	if ts.AgentToken == "" {
		return nil, fmt.Errorf("token为空")
	}
	return &ts, nil
}

// saveToken 将 token 保存到本地文件
func saveToken(path string, ts *TokenStore) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return fmt.Errorf("创建目录失败: %w", err)
	}
	data, err := json.MarshalIndent(ts, "", "  ")
	if err != nil {
		return fmt.Errorf("序列化token失败: %w", err)
	}
	if err := os.WriteFile(path, data, 0600); err != nil {
		return fmt.Errorf("写入token文件失败: %w", err)
	}
	return nil
}
