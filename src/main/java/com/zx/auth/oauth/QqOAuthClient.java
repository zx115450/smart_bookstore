package com.zx.auth.oauth;

import com.zx.auth.config.AuthQqOAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ 互联 Open API 客户端（仅服务端调用）。
 * <p>
 * OAuth 回调拿到 {@code code} 后，按 QQ 文档依次：换 {@code access_token} → 取 {@code openid} →（可选）拉昵称。
 * {@code client_secret}（AppKey）只在此类请求中使用，不可暴露给前端。
 */
@Component
@RequiredArgsConstructor
public class QqOAuthClient {

    private static final String TOKEN_URL = "https://graph.qq.com/oauth2.0/token";
    private static final String ME_URL = "https://graph.qq.com/oauth2.0/me";
    private static final String USER_INFO_URL = "https://graph.qq.com/user/get_user_info";
    /** 轻量 JSON 字符串字段解析，避免额外引入 Jackson 依赖。 */
    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    private final RestTemplate restTemplate;
    private final AuthQqOAuthProperties properties;

    /**
     * 用授权码换取 QQ {@code access_token}（一次性，code 用过即失效）。
     *
     * @param code QQ 回调 query 中的 authorization code
     * @return access_token；失败抛 {@link QqOAuthException}
     */
    public String exchangeCodeForAccessToken(String code) {
        String url = TOKEN_URL
                + "?grant_type=authorization_code"
                + "&client_id=" + encode(properties.getAppId())
                + "&client_secret=" + encode(properties.getAppKey())
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(properties.getRedirectUri());

        String body = restTemplate.getForObject(url, String.class);
        if (body == null || body.isBlank()) {
            throw new QqOAuthException("QQ token 响应为空");
        }

        // 成功时 body 为 query string：access_token=...&expires_in=...；失败时可能返回 JSON
        if (body.trim().startsWith("{")) {
            throwIfJsonError(body);
        }

        Map<String, String> params = parseQueryString(body);
        String accessToken = params.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new QqOAuthException("QQ 未返回 access_token: " + body);
        }
        return accessToken;
    }

    /**
     * 根据 access_token 获取用户在当前 App 下的 openid。
     *
     * @param accessToken {@link #exchangeCodeForAccessToken} 的返回值
     * @return openid，作为 {@code auth_user_identity.identity_value} 绑定依据
     */
    public String getOpenId(String accessToken) {
        String url = ME_URL
                + "?access_token=" + encode(accessToken)
                + "&fmt=json";

        String body = restTemplate.getForObject(url, String.class);
        if (body == null || body.isBlank()) {
            throw new QqOAuthException("QQ openid 响应为空");
        }

        // QQ 可能返回 JSONP：callback({"client_id":"...","openid":"..."});
        String json = stripJsonp(body);
        throwIfJsonError(json);
        String openid = readJsonString(json, "openid");
        if (openid == null || openid.isBlank()) {
            throw new QqOAuthException("QQ 未返回 openid: " + body);
        }
        return openid;
    }

    /**
     * 拉取 QQ 用户昵称，用于首次注册时生成 username；失败时返回 null，不影响登录主流程。
     *
     * @param accessToken 同上
     * @param openid      {@link #getOpenId} 的返回值
     * @return 昵称，或 null（接口失败 / ret != 0）
     */
    public String getNickname(String accessToken, String openid) {
        String url = USER_INFO_URL
                + "?access_token=" + encode(accessToken)
                + "&oauth_consumer_key=" + encode(properties.getAppId())
                + "&openid=" + encode(openid);

        String body = restTemplate.getForObject(url, String.class);
        if (body == null || body.isBlank()) {
            return null;
        }

        String ret = readJsonString(body, "ret");
        if (!"0".equals(ret)) {
            return null;
        }
        return readJsonString(body, "nickname");
    }

    /** QQ 错误响应含 error / error_description 字段时抛异常。 */
    private void throwIfJsonError(String body) {
        String error = readJsonString(body, "error");
        if (error != null && !error.isBlank()) {
            String description = readJsonString(body, "error_description");
            throw new QqOAuthException("QQ OAuth 失败: " + (description == null ? error : description));
        }
    }

    /** 从 JSON 文本中读取指定字符串字段（仅支持 "key":"value" 形式）。 */
    private String readJsonString(String json, String field) {
        Matcher matcher = JSON_STRING_FIELD.matcher(json);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    /** 解析 {@code key=value&...} 格式响应体（QQ token 接口成功时返回此格式）。 */
    private Map<String, String> parseQueryString(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    /** 去掉 JSONP 包装，提取括号内的 JSON 片段。 */
    private String stripJsonp(String body) {
        String trimmed = body.trim();
        int start = trimmed.indexOf('(');
        int end = trimmed.lastIndexOf(')');
        if (start >= 0 && end > start) {
            return trimmed.substring(start + 1, end);
        }
        return trimmed;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** QQ Open API 调用失败时抛出，由上层转换为登录失败或重定向错误页。 */
    public static class QqOAuthException extends RuntimeException {
        public QqOAuthException(String message) {
            super(message);
        }
    }
}
