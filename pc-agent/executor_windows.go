//go:build windows

package main

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
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
// params 为 JSON 格式：
//
//	{"message": "内容"}  — 简单弹窗
//	{"message": "内容", "extendPrompt": true, "ruleId": 123, "extendMinutes": 10}  — 含延长按钮
func (e *platformExecutor) showMessage(params string) CommandResult {
	var req struct {
		Message       string `json:"message"`
		ExtendPrompt  bool   `json:"extendPrompt"`
		RuleID        uint64 `json:"ruleId"`
		ExtendMinutes int    `json:"extendMinutes"`
	}
	if err := json.Unmarshal([]byte(params), &req); err != nil {
		return CommandResult{Success: false, Error: fmt.Sprintf("参数解析失败: %v", err)}
	}
	if req.Message == "" {
		return CommandResult{Success: false, Error: "消息内容不能为空"}
	}

	if req.ExtendPrompt && req.RuleID > 0 && req.ExtendMinutes > 0 {
		// 交互式延长对话框
		e.launchExtendPrompt(req.Message, req.RuleID, req.ExtendMinutes)
	} else {
		// 简单弹窗：msg.exe 优先（系统原生弹窗，始终置顶），失败则回退到 PowerShell MessageBox
		msg := exec.Command("msg", "*", "/TIME:120", req.Message)
		if err := msg.Run(); err != nil {
			// 回退：PowerShell MessageBox（DefaultDesktopOnly 跨 Session 显示并置顶）
			escaped := strings.ReplaceAll(req.Message, "'", "''")
			psCmd := fmt.Sprintf(
				`Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.MessageBox]::Show('%s', '系统消息', 'OK', 'Information', 'Button1', 'DefaultDesktopOnly')`,
				escaped,
			)
			ps := exec.Command("powershell", "-NoProfile", "-WindowStyle Hidden", "-NonInteractive", "-Command", psCmd)
			ps.Run()
		}
	}
	return CommandResult{Success: true, Result: "消息已发送"}
}

// runInUserSession 通过 schtasks 在用户交互会话中运行 PowerShell 命令
func (e *platformExecutor) runInUserSession(psCommand string) error {
	timestamp := time.Now().UnixNano()
	taskName := fmt.Sprintf("PCAgent_%d", timestamp)
	psPath := filepath.Join(os.TempDir(), taskName+".ps1")

	if err := os.WriteFile(psPath, append([]byte{0xEF, 0xBB, 0xBF}, []byte(psCommand)...), 0644); err != nil {
		return err
	}

	psCmdLine := fmt.Sprintf(`powershell -WindowStyle Hidden -ExecutionPolicy Bypass -File "%s"`, psPath)

	// 创建计划任务并以交互用户身份运行（Session 0 → 用户桌面 Session 1）
	if err := exec.Command("schtasks", "/create", "/tn", taskName, "/ru", "INTERACTIVE",
		"/tr", psCmdLine, "/sc", "ONCE", "/st", "00:00", "/f").Run(); err != nil {
		os.Remove(psPath)
		return err
	}

	if err := exec.Command("schtasks", "/run", "/tn", taskName).Run(); err != nil {
		exec.Command("schtasks", "/delete", "/tn", taskName, "/f").Run()
		os.Remove(psPath)
		return err
	}

	// 2 分钟后清理临时文件和计划任务
	time.AfterFunc(2*time.Minute, func() {
		_ = exec.Command("schtasks", "/delete", "/tn", taskName, "/f").Run()
		_ = os.Remove(psPath)
	})
	return nil
}

// runVBSScript 通过 schtasks + wscript.exe 在用户交互会话中运行 VBScript（无控制台窗口）
func (e *platformExecutor) runVBSScript(vbsContent string) error {
	timestamp := time.Now().UnixNano()
	taskName := fmt.Sprintf("PCAgent_%d", timestamp)
	vbsPath := filepath.Join(os.TempDir(), taskName+".vbs")

	if err := os.WriteFile(vbsPath, []byte(vbsContent), 0644); err != nil {
		return err
	}

	vbsCmdLine := fmt.Sprintf(`wscript.exe "%s"`, vbsPath)

	// 创建计划任务并以交互用户身份运行
	if err := exec.Command("schtasks", "/create", "/tn", taskName, "/ru", "INTERACTIVE",
		"/tr", vbsCmdLine, "/sc", "ONCE", "/st", "00:00", "/f").Run(); err != nil {
		os.Remove(vbsPath)
		return err
	}

	if err := exec.Command("schtasks", "/run", "/tn", taskName).Run(); err != nil {
		exec.Command("schtasks", "/delete", "/tn", taskName, "/f").Run()
		os.Remove(vbsPath)
		return err
	}

	// 2 分钟后清理临时文件和计划任务
	time.AfterFunc(2*time.Minute, func() {
		_ = exec.Command("schtasks", "/delete", "/tn", taskName, "/f").Run()
		_ = os.Remove(vbsPath)
	})
	return nil
}

// launchExtendPrompt 启动交互式延长对话框（PowerShell MessageBox + DefaultDesktopOnly 确保置顶）
func (e *platformExecutor) launchExtendPrompt(message string, ruleID uint64, minutes int) {
	extendURL := fmt.Sprintf("%s/api/supervision/extend/%d", e.backendURL, ruleID)
	escapedMsg := strings.ReplaceAll(message, "'", "''")

	psScript := fmt.Sprintf(
		`Add-Type -AssemblyName System.Windows.Forms
$r=[System.Windows.Forms.MessageBox]::Show('%s', '使用时间预警', 'YesNo', 'Warning', 'Button1', 'DefaultDesktopOnly')
if($r -eq 'Yes'){$wc=New-Object System.Net.WebClient;$wc.Headers.Add('Content-Type','application/json');$null=$wc.UploadString('%s','POST','{"minutes":%d}')}
`,
		escapedMsg, extendURL, minutes,
	)

	if err := e.runInUserSession(psScript); err != nil {
		// 回退：仅显示 msg.exe 文本消息
		msg := exec.Command("msg", "*", "/TIME:60", message)
		msg.Run()
	}
}




