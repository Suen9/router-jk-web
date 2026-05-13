package main

import (
	"fmt"
	"net"
	"os"
	"runtime"
	"strings"
)

// getMacAddr 获取本机第一个非回环网卡的 MAC 地址
func getMacAddr() (string, error) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return "", fmt.Errorf("获取网络接口失败: %w", err)
	}
	for _, iface := range interfaces {
		// 排除回环、虚拟网卡和已禁用的接口
		if iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		if iface.Flags&net.FlagUp == 0 {
			continue
		}
		mac := iface.HardwareAddr.String()
		if mac != "" && mac != "00:00:00:00:00:00" {
			return mac, nil
		}
	}
	return "", fmt.Errorf("未找到有效的 MAC 地址")
}

// getLocalIP 获取本机内网 IP 地址
func getLocalIP() (string, error) {
	// 方法1：尝试连接外部地址获取本机IP（UDP无实际连接，仅用于获取出站IP）
	conn, err := net.Dial("udp", "10.0.0.1:80")
	if err == nil {
		defer conn.Close()
		localAddr := conn.LocalAddr().(*net.UDPAddr)
		return localAddr.IP.String(), nil
	}

	// 方法2：遍历网卡获取第一个非回环 IPv4 地址
	interfaces, err := net.Interfaces()
	if err != nil {
		return "", fmt.Errorf("获取本机IP失败: %w", err)
	}
	for _, iface := range interfaces {
		if iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		if iface.Flags&net.FlagUp == 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			ipnet, ok := addr.(*net.IPNet)
			if !ok {
				continue
			}
			ipv4 := ipnet.IP.To4()
			if ipv4 != nil && !ipv4.IsLoopback() {
				return ipv4.String(), nil
			}
		}
	}
	return "", fmt.Errorf("未找到有效IP地址")
}

// getHostname 获取主机名
func getHostname() string {
	name, err := os.Hostname()
	if err != nil {
		return "unknown"
	}
	return name
}

// getOSVersion 获取操作系统版本信息
func getOSVersion() string {
	return runtime.GOOS + " " + runtime.GOARCH
}

// registerAgent 执行注册流程 — 收集本机信息，调用后端注册接口，保存 token
func registerAgent(client *BackendClient, tokenFile string) error {
	mac, err := getMacAddr()
	if err != nil {
		return fmt.Errorf("获取MAC地址失败: %w", err)
	}
	// 格式化 MAC 地址为统一格式
	mac = strings.ToUpper(strings.ReplaceAll(mac, "-", ":"))

	ip, err := getLocalIP()
	if err != nil {
		ip = "0.0.0.0"
	}

	hostname := getHostname()
	osVersion := getOSVersion()

	fmt.Printf("[注册] MAC=%s IP=%s 主机名=%s 系统=%s\n", mac, ip, hostname, osVersion)

	resp, err := client.Register(map[string]string{
		"mac":          mac,
		"hostname":     hostname,
		"ip":           ip,
		"osVersion":    osVersion,
		"agentVersion": "1.0.0",
	})
	if err != nil {
		return fmt.Errorf("注册失败: %w", err)
	}

	// 保存 token 到本地文件
	tokenStore := &TokenStore{
		DeviceID:   resp.DeviceID,
		AgentToken: resp.AgentToken,
	}
	if err := saveToken(tokenFile, tokenStore); err != nil {
		return fmt.Errorf("保存token失败: %w", err)
	}

	// 设置到客户端
	client.SetToken(resp.AgentToken)

	fmt.Printf("[注册] 成功! DeviceID=%d Token=%s...\n", resp.DeviceID, resp.AgentToken[:16])
	return nil
}
