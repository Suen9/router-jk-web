package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// BackendClient 后端 API 客户端 — 封装所有与后端通信的 HTTP 请求
type BackendClient struct {
	baseURL    string
	token      string
	httpClient *http.Client
}

// NewBackendClient 创建后端客户端
func NewBackendClient(baseURL string) *BackendClient {
	return &BackendClient{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// SetToken 设置 Agent Token
func (c *BackendClient) SetToken(token string) {
	c.token = token
}

// RegisterResp 注册响应
type RegisterResp struct {
	DeviceID   uint64 `json:"deviceId"`
	AgentToken string `json:"agentToken"`
}

// Register 向后端注册 Agent，返回 deviceID 和 token
func (c *BackendClient) Register(req map[string]string) (*RegisterResp, error) {
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("序列化注册请求失败: %w", err)
	}

	resp, err := c.httpClient.Post(c.baseURL+"/api/pc/agent/register", "application/json", bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("注册请求失败: %w", err)
	}
	defer resp.Body.Close()

	result, err := parseResponse(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("解析注册响应失败: %w", err)
	}

	data, err := json.Marshal(result.Data)
	if err != nil {
		return nil, fmt.Errorf("序列化注册数据失败: %w", err)
	}

	var regResp RegisterResp
	if err := json.Unmarshal(data, &regResp); err != nil {
		return nil, fmt.Errorf("解析注册数据失败: %w", err)
	}
	return &regResp, nil
}

// Heartbeat 发送心跳，更新在线状态和 IP
func (c *BackendClient) Heartbeat(ip string) error {
	body, _ := json.Marshal(map[string]string{"ip": ip})
	req, err := http.NewRequest("POST", c.baseURL+"/api/pc/agent/heartbeat", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Agent-Token", c.token)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("心跳请求失败: %w", err)
	}
	defer resp.Body.Close()

	_, err = parseResponse(resp.Body)
	return err
}

// PollCommand 轮询拉取待执行指令，无指令时返回 nil
type PollResult struct {
	CommandID   uint64 `json:"commandId"`
	CommandType string `json:"commandType"`
	Params      string `json:"params"`
}

func (c *BackendClient) PollCommand() (*PollResult, error) {
	req, err := http.NewRequest("GET", c.baseURL+"/api/pc/agent/poll", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-Agent-Token", c.token)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("轮询指令失败: %w", err)
	}
	defer resp.Body.Close()

	result, err := parseResponse(resp.Body)
	if err != nil {
		return nil, err
	}
	// 无指令返回 null
	if result.Data == nil {
		return nil, nil
	}

	data, err := json.Marshal(result.Data)
	if err != nil {
		return nil, err
	}

	var poll PollResult
	if err := json.Unmarshal(data, &poll); err != nil {
		return nil, err
	}
	return &poll, nil
}

// ReportResult 上报指令执行结果
func (c *BackendClient) ReportResult(commandID uint64, success bool, result, errMsg string) error {
	body, _ := json.Marshal(map[string]interface{}{
		"commandId":    commandID,
		"success":      success,
		"result":       result,
		"errorMessage": errMsg,
	})
	req, err := http.NewRequest("POST", c.baseURL+"/api/pc/agent/report", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Agent-Token", c.token)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("上报结果失败: %w", err)
	}
	defer resp.Body.Close()

	_, err = parseResponse(resp.Body)
	return err
}

// BaseResponse 后端通用响应结构
type BaseResponse struct {
	Code    int         `json:"code"`
	Data    interface{} `json:"data"`
	Message string      `json:"message"`
}

// parseResponse 解析后端响应并检查业务状态码
func parseResponse(r io.Reader) (*BaseResponse, error) {
	data, err := io.ReadAll(r)
	if err != nil {
		return nil, fmt.Errorf("读取响应失败: %w", err)
	}
	var resp BaseResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("解析响应JSON失败: %w (body: %s)", err, string(data))
	}
	if resp.Code != 0 {
		return nil, fmt.Errorf("业务错误 code=%d message=%s", resp.Code, resp.Message)
	}
	return &resp, nil
}
