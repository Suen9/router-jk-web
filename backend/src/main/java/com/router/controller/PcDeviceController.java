package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.entity.PcDevice;
import com.router.model.vo.PcDeviceVO;
import com.router.service.PcCommandService;
import com.router.service.PcDeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PC设备管理 Controller — 提供受控PC设备的CRUD操作和指令下发功能
 */
@RestController
@RequestMapping("/api/pc/device")
public class PcDeviceController {

    private final PcDeviceService pcDeviceService;
    private final PcCommandService pcCommandService;

    public PcDeviceController(PcDeviceService pcDeviceService, PcCommandService pcCommandService) {
        this.pcDeviceService = pcDeviceService;
        this.pcCommandService = pcCommandService;
    }

    /**
     * 获取PC设备列表（支持关键字搜索）
     */
    @GetMapping("/list")
    public BaseResponse<List<PcDeviceVO>> list(
            @RequestParam(required = false) String keyword) {
        return ResultUtils.success(pcDeviceService.list(keyword));
    }

    /**
     * 获取PC设备详情
     */
    @GetMapping("/{id}")
    public BaseResponse<PcDevice> detail(@PathVariable Long id) {
        PcDevice device = pcDeviceService.getById(id);
        if (device == null) {
            return ResultUtils.error(40400, "设备不存在");
        }
        return ResultUtils.success(device);
    }

    /**
     * 手动添加PC设备
     */
    @PostMapping("/add")
    public BaseResponse<PcDevice> add(@RequestBody Map<String, String> params) {
        String mac = params.get("mac");
        String hostname = params.get("hostname");
        String remark = params.get("remark");
        if (mac == null || mac.isEmpty()) {
            return ResultUtils.error(40000, "MAC地址不能为空");
        }
        return ResultUtils.success(pcDeviceService.add(mac, hostname, remark));
    }

    /**
     * 更新PC设备信息
     */
    @PutMapping("/{id}")
    public BaseResponse<Boolean> update(@PathVariable Long id, @RequestBody Map<String, String> params) {
        return ResultUtils.success(pcDeviceService.update(id, params.get("hostname"), params.get("remark")));
    }

    /**
     * 删除PC设备
     */
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        return ResultUtils.success(pcDeviceService.remove(id));
    }

    /**
     * 向PC设备发送控制指令
     */
    @PostMapping("/{id}/sendCommand")
    public BaseResponse<Object> sendCommand(@PathVariable Long id, @RequestBody Map<String, String> params) {
        String commandType = params.get("commandType");
        if (commandType == null || commandType.isEmpty()) {
            return ResultUtils.error(40000, "指令类型不能为空");
        }
        PcDevice device = pcDeviceService.getById(id);
        if (device == null) {
            return ResultUtils.error(40400, "设备不存在");
        }
        pcCommandService.create(id, commandType, params.get("params"));
        return ResultUtils.success(null);
    }
}
