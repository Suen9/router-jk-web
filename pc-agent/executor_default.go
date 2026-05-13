//go:build !windows

package main

import "fmt"

func (e *platformExecutor) getProcesses() CommandResult {
	return CommandResult{Success: false, Error: "进程列表仅支持 Windows 平台"}
}

func (e *platformExecutor) killProcess(params string) CommandResult {
	return CommandResult{Success: false, Error: "结束进程仅支持 Windows 平台"}
}

func (e *platformExecutor) lockScreen() CommandResult {
	return CommandResult{Success: false, Error: "锁屏仅支持 Windows 平台"}
}

func (e *platformExecutor) shutdown() CommandResult {
	return CommandResult{Success: false, Error: "关机仅支持 Windows 平台"}
}

func (e *platformExecutor) restart() CommandResult {
	return CommandResult{Success: false, Error: "重启仅支持 Windows 平台"}
}

func (e *platformExecutor) logoff() CommandResult {
	return CommandResult{Success: false, Error: "注销仅支持 Windows 平台"}
}
