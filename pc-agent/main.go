package main

import (
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"
)

const (
	configFile  = "config.json"             // 配置文件路径
	tokenFile   = ".agent_token.json"       // token 缓存文件
	pollInterval = 5 * time.Second          // 轮询间隔
)

func main() {
	fmt.Println("=== PC Agent ===")

	// 获取程序所在目录作为工作目录
	exePath, err := os.Executable()
	if err != nil {
		fmt.Fprintf(os.Stderr, "获取可执行文件路径失败: %v\n", err)
		os.Exit(1)
	}
	workDir := filepath.Dir(exePath)
	if err := os.Chdir(workDir); err != nil {
		fmt.Fprintf(os.Stderr, "切换工作目录失败: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("[Agent] 工作目录: %s\n", workDir)

	// 加载配置
	cfg, err := loadConfig(configFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "加载配置失败: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("[Agent] 后端地址: %s\n", cfg.BackendURL)

	// 初始化 HTTP 客户端
	client := NewBackendClient(cfg.BackendURL)

	// 尝试从本地加载 token
	tokenPath := filepath.Join(workDir, tokenFile)
	tokenStore, err := loadToken(tokenPath)
	if err != nil {
		fmt.Println("[Agent] 未找到本地 token，执行注册...")
		// 首次运行，执行注册
		if err := registerAgent(client, tokenPath); err != nil {
			fmt.Fprintf(os.Stderr, "注册失败: %v\n", err)
			os.Exit(1)
		}
	} else {
		fmt.Printf("[Agent] 加载本地 token, DeviceID=%d\n", tokenStore.DeviceID)
		client.SetToken(tokenStore.AgentToken)
	}

	// 初始化指令执行器
	executor := NewExecutor()

	// 捕获退出信号
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	fmt.Println("[Agent] 启动完成，进入主循环...")

	// 主循环：心跳 + 指令轮询
	ticker := time.NewTicker(pollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-sigCh:
			fmt.Println("\n[Agent] 收到退出信号，停止运行")
			return

		case <-ticker.C:
			doHeartbeat(client)
			doPollAndExecute(client, executor)
		}
	}
}

// doHeartbeat 执行心跳上报
func doHeartbeat(client *BackendClient) {
	ip, err := getLocalIP()
	if err != nil {
		ip = "0.0.0.0"
	}
	if err := client.Heartbeat(ip); err != nil {
		fmt.Printf("[心跳] 失败: %v\n", err)
	}
}

// doPollAndExecute 轮询指令并执行
func doPollAndExecute(client *BackendClient, executor Executor) {
	cmd, err := client.PollCommand()
	if err != nil {
		// 无指令时不输出日志，避免日志过多
		return
	}
	if cmd == nil {
		return // 无待执行指令
	}

	fmt.Printf("[指令] 收到指令: type=%s id=%d\n", cmd.CommandType, cmd.CommandID)

	// 执行指令
	result := executor.Execute(cmd.CommandType, cmd.Params)

	// 上报结果
	if err := client.ReportResult(cmd.CommandID, result.Success, result.Result, result.Error); err != nil {
		fmt.Printf("[指令] 上报结果失败: %v\n", err)
	} else {
		fmt.Printf("[指令] 执行完成: type=%s success=%v\n", cmd.CommandType, result.Success)
	}
}
