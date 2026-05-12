//go:build windows

package main

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"os/exec"
	"strconv"
	"strings"
)

// ProcessInfo 进程信息结构
type ProcessInfo struct {
	Name        string `json:"name"`
	PID         int    `json:"pid"`
	SessionName string `json:"sessionName"`
	MemUsage    string `json:"memUsage"`
}

// getProcesses 执行 tasklist 命令获取进程列表，返回 JSON 格式结果
func (e *platformExecutor) getProcesses() CommandResult {
	cmd := exec.Command("tasklist", "/FO", "CSV", "/NH")
	output, err := cmd.Output()
	if err != nil {
		return CommandResult{
			Success: false,
			Error:   fmt.Sprintf("执行tasklist失败: %v", err),
		}
	}

	reader := csv.NewReader(strings.NewReader(string(output)))
	records, err := reader.ReadAll()
	if err != nil {
		return CommandResult{
			Success: false,
			Error:   fmt.Sprintf("解析tasklist输出失败: %v", err),
		}
	}

	var processes []ProcessInfo
	for _, record := range records {
		if len(record) < 5 {
			continue
		}
		pid, _ := strconv.Atoi(strings.TrimSpace(record[1]))
		processes = append(processes, ProcessInfo{
			Name:        strings.Trim(record[0], "\""),
			PID:         pid,
			SessionName: strings.Trim(record[2], "\""),
			MemUsage:    strings.Trim(record[4], "\""),
		})
	}

	result, err := toJSON(processes)
	if err != nil {
		return CommandResult{
			Success: false,
			Error:   fmt.Sprintf("序列化进程列表失败: %v", err),
		}
	}
	return CommandResult{Success: true, Result: result}
}

// lockScreen 锁屏 — 使用 rundll32 调用 LockWorkStation
func (e *platformExecutor) lockScreen() CommandResult {
	cmd := exec.Command("rundll32.exe", "user32.dll,LockWorkStation")
	if err := cmd.Run(); err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("锁屏失败: %v", err)}
	}
	return CommandResult{Success: true, Result: "锁屏成功"}
}

// shutdown 远程关机 — 30秒延迟，允许用户取消
func (e *platformExecutor) shutdown() CommandResult {
	cmd := exec.Command("shutdown", "/s", "/t", "30", "/c", "系统管理员已发起远程关机")
	if err := cmd.Run(); err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("关机失败: %v", err)}
	}
	return CommandResult{Success: true, Result: "关机指令已执行（30秒后关机）"}
}

// restart 远程重启 — 30秒延迟
func (e *platformExecutor) restart() CommandResult {
	cmd := exec.Command("shutdown", "/r", "/t", "30", "/c", "系统管理员已发起远程重启")
	if err := cmd.Run(); err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("重启失败: %v", err)}
	}
	return CommandResult{Success: true, Result: "重启指令已执行（30秒后重启）"}
}

// logoff 注销当前用户
func (e *platformExecutor) logoff() CommandResult {
	cmd := exec.Command("shutdown", "/l")
	if err := cmd.Run(); err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("注销失败: %v", err)}
	}
	return CommandResult{Success: true, Result: "注销成功"}
}

func toJSON(v interface{}) (string, error) {
	data, err := json.Marshal(v)
	if err != nil {
		return "", err
	}
	return string(data), nil
}
