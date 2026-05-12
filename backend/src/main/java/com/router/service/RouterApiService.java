package com.router.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.router.config.RouterConfig;
import com.router.mapper.RouterSessionMapper;
import com.router.model.entity.RouterSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class RouterApiService {

    private static final Logger log = LoggerFactory.getLogger(RouterApiService.class);
    private static final String SESSION_REDIS_KEY = "router:session_id";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RouterConfig routerConfig;
    private final RouterSessionMapper routerSessionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public RouterApiService(RouterConfig routerConfig, RouterSessionMapper routerSessionMapper,
                            RedisTemplate<String, Object> redisTemplate) {
        this.routerConfig = routerConfig;
        this.routerSessionMapper = routerSessionMapper;
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        String cached = (String) redisTemplate.opsForValue().get(SESSION_REDIS_KEY);
        if (cached == null && routerSessionMapper.selectList(null).size() > 0) {
            RouterSession row = routerSessionMapper.selectList(null).get(0);
            if (row.getSessionId() != null) {
                redisTemplate.opsForValue().set(SESSION_REDIS_KEY, row.getSessionId(), SESSION_TTL);
            }
        }
        if (cached == null && routerSessionMapper.selectList(null).isEmpty()) {
            login();
        }
    }

    public synchronized String getSessionId() {
        String sessionId = (String) redisTemplate.opsForValue().get(SESSION_REDIS_KEY);
        if (sessionId != null) return sessionId;
        login();
        return (String) redisTemplate.opsForValue().get(SESSION_REDIS_KEY);
    }

    private void login() {
        try {
            long time = 1776524601;
            Map<String, Object> params = new HashMap<>();
            params.put("method", "login");
            Map<String, Object> inner = new HashMap<>();
            inner.put("pw", routerConfig.getPassword());
            inner.put("un", routerConfig.getUsername());
            inner.put("time", String.valueOf(time));
            params.put("params", inner);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    routerConfig.getBaseUrl() + "/login", request, String.class);
            log.info("Router login response: {}", response.getBody());

            if (response.getHeaders().containsKey("Set-Cookie")) {
                for (String cookie : response.getHeaders().get("Set-Cookie")) {
                    if (cookie.contains("SessionID=")) {
                        String sid = cookie.split("SessionID=")[1].split(";")[0];
                        persistSession(sid);
                        return;
                    }
                }
            }

            JsonNode node = objectMapper.readTree(response.getBody());
            if (node.has("code") && node.get("code").asInt() == 0) {
                HttpHeaders respHeaders = response.getHeaders();
                if (respHeaders.containsKey("Set-Cookie")) {
                    for (String cookie : respHeaders.get("Set-Cookie")) {
                        if (cookie.contains("SessionID=")) {
                            persistSession(cookie.split("SessionID=")[1].split(";")[0]);
                            return;
                        }
                    }
                }
            }
            log.error("Login failed: {}", response.getBody());
        } catch (Exception e) {
            log.error("Login exception", e);
        }
    }

    private void persistSession(String sessionId) {
        redisTemplate.opsForValue().set(SESSION_REDIS_KEY, sessionId, SESSION_TTL);
        routerSessionMapper.delete(null);
        RouterSession rs = new RouterSession();
        rs.setSessionId(sessionId);
        routerSessionMapper.insert(rs);
        log.info("SessionID persisted: {}", sessionId);
    }

    public <T> T get(String path, Class<T> clazz) {
        return executeWithRetry(path, null, HttpMethod.GET, clazz);
    }

    public <T> T post(String path, Object body, Class<T> clazz) {
        return executeWithRetry(path, body, HttpMethod.POST, clazz);
    }

    public JsonNode getForJson(String path) {
        String result = executeWithRetry(path, null, HttpMethod.GET, String.class);
        try {
            return objectMapper.readTree(result);
        } catch (Exception e) {
            log.error("Parse JSON failed", e);
            return null;
        }
    }

    public JsonNode postForJson(String path, Object body) {
        String result = executeWithRetry(path, body, HttpMethod.POST, String.class);
        try {
            return objectMapper.readTree(result);
        } catch (Exception e) {
            log.error("Parse JSON failed", e);
            return null;
        }
    }

    private <T> T executeWithRetry(String path, Object body, HttpMethod method, Class<T> clazz) {
        try {
            String sessionId = getSessionId();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", "__APP_LANG__=zh_cn; SessionTimeout=1000; devHost=cmcc.wifi; tipWireless=true; showProvince=false; SessionID=" + sessionId);

            HttpEntity<?> request = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);
            String url = routerConfig.getBaseUrl() + path;

            ResponseEntity<String> response = restTemplate.exchange(url, method, request, String.class);
            String responseBody = response.getBody();

            JsonNode node = objectMapper.readTree(responseBody);
            if (node.has("code")) {
                int code = node.get("code").asInt();
                String msg = node.has("msg") ? node.get("msg").asText() : "";
                if (code == -1 && "AuthRequired".equals(msg)) {
                    log.warn("Session expired, re-logging...");
                    login();
                    return execute(path, body, method, clazz);
                }
            }

            if (clazz == String.class) return clazz.cast(responseBody);
            return objectMapper.readValue(responseBody, clazz);
        } catch (Exception e) {
            log.error("API call failed: {} {}", method, path, e);
            throw new RuntimeException("Router API error: " + e.getMessage(), e);
        }
    }

    private <T> T execute(String path, Object body, HttpMethod method, Class<T> clazz) {
        try {
            String sessionId = getSessionId();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", "SessionID=" + sessionId);

            HttpEntity<?> request = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);
            String url = routerConfig.getBaseUrl() + path;
            ResponseEntity<String> response = restTemplate.exchange(url, method, request, String.class);

            if (clazz == String.class) return clazz.cast(response.getBody());
            return objectMapper.readValue(response.getBody(), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Router API error: " + e.getMessage(), e);
        }
    }
}
