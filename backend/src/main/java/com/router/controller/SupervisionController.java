package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.DeleteRequest;
import com.router.common.ResultUtils;
import com.router.model.dto.SupervisionVO;
import com.router.model.entity.SupervisionRule;
import com.router.service.SupervisionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/supervision")
public class SupervisionController {

    private final SupervisionService supervisionService;

    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    @GetMapping("/list")
    public BaseResponse<List<SupervisionVO>> list() {
        return ResultUtils.success(supervisionService.list());
    }

    @PostMapping("/add")
    public BaseResponse<SupervisionRule> add(@RequestBody SupervisionRule rule) {
        return ResultUtils.success(supervisionService.add(rule));
    }

    @PutMapping("/rule/{id}")
    public BaseResponse<SupervisionRule> updateRule(@PathVariable Long id, @RequestBody SupervisionRule rule) {
        return ResultUtils.success(supervisionService.updateRule(id, rule));
    }

    @PostMapping("/extend/{id}")
    public BaseResponse<Boolean> extend(@PathVariable Long id, @RequestBody Map<String, Integer> params) {
        int minutes = params.getOrDefault("minutes", 30);
        return ResultUtils.success(supervisionService.extendTime(id, minutes));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        return ResultUtils.success(supervisionService.remove(id));
    }
}
