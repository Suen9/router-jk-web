package main

import (
	"fmt"
	"io"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"
)

const (
	configFile   = "config.json"       // 配置文件路径
	tokenFile    = ".agent_token.json" // token 缓存文件
	logFile      = "pc-agent.log"      // 日志文件
	pollInterval = 5 * time.Second     // 轮询间隔
)

var logger *log.Logger

func main() {
	// 获取程序所在目录作为工作目录
	exePath, err := os.Executable()
	if err != nil {
		pauseError("获取可执行文件路径失败: %v", err)
		os.Exit(1)
	}
	workDir := filepath.Dir(exePath)
	if err := os.Chdir(workDir); err != nil {
		pauseError("切换工作目录失败: %v", err)
		os.Exit(1)
	}

	// 初始化日志：同时输出到控制台和文件
	logPath := filepath.Join(workDir, logFile)
	f, err := os.OpenFile(logPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		pauseError("打开日志文件失败: %v", err)
		os.Exit(1)
	}
	defer f.Close()
	multi := io.MultiWriter(os.Stdout, f)
	logger = log.New(multi, "", log.LstdFlags)

	logger.Printf("=== PC Agent 启动 === 工作目录: %s", workDir)

	// 加载配置
	cfg, err := loadConfig(configFile)
	if err != nil {
		logger.Printf("[错误] 加载配置失败: %v", err)
		pause("请检查 config.json 是否存在且格式正确，然后按 Enter 键退出...")
		os.Exit(1)
	}
	logger.Printf("[配置] 后端地址: %s", cfg.BackendURL)

	// 初始化 HTTP 客户端
	client := NewBackendClient(cfg.BackendURL)

	// 尝试从本地加载 token
	tokenPath := filepath.Join(workDir, tokenFile)
	tokenStore, loadErr := loadToken(tokenPath)
	if loadErr != nil {
		logger.Printf("[注册] 未找到本地 token，执行注册...")
		if err := registerAgent(client, tokenPath); err != nil {
			logger.Printf("[错误] 注册失败: %v", err)
			logger.Printf("[提示] 请确认后端服务 (%s) 已启动且网络可达", cfg.BackendURL)
			pause("按 Enter 键退出...")
			os.Exit(1)
		}
		// 重新加载 token
		tokenStore, _ = loadToken(tokenPath)
	} else {
		logger.Printf("[认证] 加载本地 token, DeviceID=%d", tokenStore.DeviceID)
		client.SetToken(tokenStore.AgentToken)
	}

	// 初始化指令执行器
	executor := NewExecutor(cfg.BackendURL)

	// 捕获退出信号
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	logger.Printf("[就绪] Agent 启动成功，进入运行循环（每 %v 轮询一次）", pollInterval)
	logger.Printf("[提示] 日志文件: %s", logPath)

	// 主循环：心跳 + 指令轮询
	ticker := time.NewTicker(pollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-sigCh:
			logger.Println("[退出] 收到退出信号，停止运行")
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
		logger.Printf("[心跳] 失败: %v", err)
	}
}

// doPollAndExecute 轮询指令并执行
func doPollAndExecute(client *BackendClient, executor Executor) {
	cmd, err := client.PollCommand()
	if err != nil {
		return
	}
	if cmd == nil {
		return
	}

	logger.Printf("[指令] 收到指令: type=%s id=%d", cmd.CommandType, cmd.CommandID)
	result := executor.Execute(cmd.CommandType, cmd.Params)

	if err := client.ReportResult(cmd.CommandID, result.Success, result.Result, result.Error); err != nil {
		logger.Printf("[指令] 上报结果失败: %v", err)
	} else {
		logger.Printf("[指令] 执行完成: type=%s success=%v", cmd.CommandType, result.Success)
	}
}

// pauseError 输出错误日志并显示暂停提示
func pauseError(format string, args ...interface{}) {
	msg := fmt.Sprintf(format, args...)
	// 尝试写入日志文件
	if f, e := os.OpenFile(logFile, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644); e == nil {
		defer f.Close()
		fmt.Fprintf(f, "[%s] [严重] %s\n", time.Now().Format("2006/01/02 15:04:05"), msg)
	}
	fmt.Fprintf(os.Stderr, "[严重] %s\n", msg)
	pause("发生严重错误，按 Enter 键退出...")
}

// pause 显示提示并等待用户按 Enter
func pause(msg string) {
	fmt.Print(msg)
	var buf [1]byte
	os.Stdin.Read(buf[:])
}
