package main

import "fmt"

// CommandResult 指令执行结果
type CommandResult struct {
	Success bool
	Result  string // 执行结果（进程列表JSON或成功信息）
	Error   string // 错误信息（失败时）
}

// Executor 指令执行器接口 — 根据指令类型执行对应操作
type Executor interface {
	Execute(commandType string, params string) CommandResult
}

// NewExecutor 创建当前平台对应的指令执行器
func NewExecutor(backendURL string) Executor {
	return &platformExecutor{backendURL: backendURL}
}

type platformExecutor struct {
	backendURL string // 后端地址，用于交互式弹窗调用延长 API
}

// Execute 根据指令类型分发执行
func (e *platformExecutor) Execute(commandType string, params string) CommandResult {
	switch commandType {
	case "PROCESSES":
		return e.getProcesses()
	case "LOCK_SCREEN":
		return e.lockScreen()
	case "SHUTDOWN":
		return e.shutdown()
	case "RESTART":
		return e.restart()
	case "LOGOFF":
		return e.logoff()
	case "KILL_PROCESS":
		return e.killProcess(params)
	case "SHOW_MESSAGE":
		return e.showMessage(params)
	default:
		return CommandResult{
			Success: false,
			Error:   fmt.Sprintf("不支持的指令类型: %s", commandType),
		}
	}
}
