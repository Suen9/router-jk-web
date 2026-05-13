//go:build windows

package main

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"os/exec"
	"sort"
	"strconv"
	"strings"
)

// ProcessInfo 进程信息结构
type ProcessInfo struct {
	Name        string `json:"name"`
	PID         int    `json:"pid"`
	SessionName string `json:"sessionName"`
	MemUsage    string `json:"memUsage"`
	// 仅用于排序的内存字节数，不返回给前端
	memBytes int64
}

// getProcesses 获取按内存占用降序排列的前20个进程
func (e *platformExecutor) getProcesses() CommandResult {
	cmd := exec.Command("tasklist", "/FO", "CSV", "/NH")
	output, err := cmd.Output()
	if err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("执行tasklist失败: %v", err)}
	}

	reader := csv.NewReader(strings.NewReader(string(output)))
	records, err := reader.ReadAll()
	if err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("解析tasklist输出失败: %v", err)}
	}

	var processes []ProcessInfo
	for _, record := range records {
		if len(record) < 5 {
			continue
		}
		pid, _ := strconv.Atoi(strings.TrimSpace(record[1]))
		memStr := strings.Trim(record[4], "\"")
		p := ProcessInfo{
			Name:        strings.Trim(record[0], "\""),
			PID:         pid,
			SessionName: strings.Trim(record[2], "\""),
			MemUsage:    memStr,
			memBytes:    parseMemBytes(memStr),
		}
		processes = append(processes, p)
	}

	// 按内存占用降序排列，取前20
	sort.Slice(processes, func(i, j int) bool {
		return processes[i].memBytes > processes[j].memBytes
	})
	if len(processes) > 20 {
		processes = processes[:20]
	}

	// 清除内部字段后返回
	type ProcessVO struct {
		Name        string `json:"name"`
		PID         int    `json:"pid"`
		SessionName string `json:"sessionName"`
		MemUsage    string `json:"memUsage"`
	}
	vo := make([]ProcessVO, len(processes))
	for i, p := range processes {
		vo[i] = ProcessVO{Name: p.Name, PID: p.PID, SessionName: p.SessionName, MemUsage: p.MemUsage}
	}

	result, err := json.Marshal(vo)
	if err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("序列化进程列表失败: %v", err)}
	}
	return CommandResult{Success: true, Result: string(result)}
}

// parseMemBytes 将 tasklist 的内存字符串转为字节数用于排序
// 格式如 "8,888 K" 或 "123,456 K"
func parseMemBytes(memStr string) int64 {
	// 去掉逗号和单位
	cleaned := strings.TrimSuffix(memStr, " K")
	cleaned = strings.TrimSuffix(cleaned, " M")
	cleaned = strings.ReplaceAll(cleaned, ",", "")
	cleaned = strings.TrimSpace(cleaned)
	val, err := strconv.ParseInt(cleaned, 10, 64)
	if err != nil {
		return 0
	}
	if strings.HasSuffix(memStr, " M") {
		return val * 1024 * 1024
	}
	return val * 1024 // 默认是 K
}

// killProcess 结束指定 PID 的进程，params 为 JSON 格式 {"pid": 1234}
func (e *platformExecutor) killProcess(params string) CommandResult {
	var req struct {
		PID int `json:"pid"`
	}
	if err := json.Unmarshal([]byte(params), &req); err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("参数解析失败: %v", err)}
	}
	if req.PID <= 0 {
		return CommandResult{Success: false, Error: "无效的PID"}
	}

	cmd := exec.Command("taskkill", "/F", "/PID", strconv.Itoa(req.PID))
	output, err := cmd.CombinedOutput()
	if err != nil {
		return CommandResult{
			Success: false,
			Error:   fmt.Sprintf("结束进程失败(PID=%d): %v - %s", req.PID, err, strings.TrimSpace(string(output))),
		}
	}
	return CommandResult{
		Success: true,
		Result:  fmt.Sprintf("进程 %d 已终止", req.PID),
	}
}

// lockScreen 锁屏
func (e *platformExecutor) lockScreen() CommandResult {
	cmd := exec.Command("rundll32.exe", "user32.dll,LockWorkStation")
	if err := cmd.Run(); err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("锁屏失败: %v", err)}
	}
	return CommandResult{Success: true, Result: "锁屏成功"}
}

// shutdown 远程关机 — 30秒延迟
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

// showMessage 远程弹窗显示消息
// params 为 JSON 格式 {"message": "要显示的内容"}
func (e *platformExecutor) showMessage(params string) CommandResult {
	var req struct {
		Message string `json:"message"`
	}
	if err := json.Unmarshal([]byte(params), &req); err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("参数解析失败: %v", err)}
	}
	if req.Message == "" {
		return CommandResult{Success: false, Error: "消息内容不能为空"}
	}

	// 方式1：使用 msg.exe（系统原生弹窗，始终置顶，Session 0 隔离下也能正常工作）
	msg := exec.Command("msg", "*", "/TIME:120", req.Message)
	if err := msg.Run(); err == nil {
		return CommandResult{Success: true, Result: "消息已发送"}
	}

	// 方式2：回退到 PowerShell MessageBox（使用 DefaultDesktopOnly 确保置顶显示）
	escaped := strings.ReplaceAll(req.Message, "'", "''")
	psCmd := fmt.Sprintf(
		`Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.MessageBox]::Show('%s', '系统消息', 'OK', 'Information', 'Button1', 'DefaultDesktopOnly')`,
		escaped,
	)
	ps := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd)
	if err := ps.Run(); err != nil {
		return CommandResult{
			Success: false,
			Error:   fmt.Sprintf("弹窗失败(msg和PowerShell均不可用): %v", err),
		}
	}
	return CommandResult{Success: true, Result: "消息已发送"}
}
