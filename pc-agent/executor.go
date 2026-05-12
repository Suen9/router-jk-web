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
	// Execute 执行指定类型的指令，params 为可选参数
	Execute(commandType string, params string) CommandResult
}

// NewExecutor 创建当前平台对应的指令执行器
func NewExecutor() Executor {
	return &platformExecutor{}
}

// platformExecutor 平台特定的执行器 — 具体方法在 _windows.go 或 _default.go 中实现
type platformExecutor struct{}

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
	default:
		return CommandResult{
			Success: false,
			Error:   fmt.Sprintf("不支持的指令类型: %s", commandType),
		}
	}
}
