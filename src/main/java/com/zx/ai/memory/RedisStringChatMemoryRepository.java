package com.zx.ai.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于现有 {@link StringRedisTemplate} 的 ChatMemory 存储。
 * <p>
 * 官方 {@code spring-ai-starter-model-chat-memory-repository-redis} 依赖 Redis Stack
 *（RedisJSON / Query Engine），本项目用普通 Redis，因此自实现 Repository，
 * 仍走 Spring AI 的 {@link org.springframework.ai.chat.memory.ChatMemory} + Advisor 链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStringChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "ai:chat:memory:";
    private static final TypeReference<List<Map<String, String>>> LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(keys.size());
        for (String key : keys) {
            ids.add(key.substring(KEY_PREFIX.length()));
        }
        return ids;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String json = redis.opsForValue().get(key(conversationId));
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map<String, String>> rows = objectMapper.readValue(json, LIST_TYPE);
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<Message> messages = new ArrayList<>(rows.size());
            for (Map<String, String> row : rows) {
                Message message = toMessage(row);
                if (message != null) {
                    messages.add(message);
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("read chat memory failed, conversationId={}", conversationId, e);
            return List.of();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            if (messages != null) {
                for (Message message : messages) {
                    Map<String, String> row = toRow(message);
                    if (row != null) {
                        rows.add(row);
                    }
                }
            }
            String json = objectMapper.writeValueAsString(rows);
            Duration ttl = Duration.ofMinutes(Math.max(1, aiProperties.getSession().getTtlMinutes()));
            redis.opsForValue().set(key(conversationId), json, ttl);
        } catch (Exception e) {
            log.warn("save chat memory failed, conversationId={}", conversationId, e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redis.delete(key(conversationId));
    }

    /** Redis Key：{@code ai:chat:memory:{conversationId}}。 */
    private static String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    /**
     * Message → 可序列化行。跳过 TOOL 类型（工具中间态不落会话，节省 token 与存储）。
     */
    private static Map<String, String> toRow(Message message) {
        if (message == null || message.getMessageType() == null) {
            return null;
        }
        MessageType type = message.getMessageType();
        if (type == MessageType.TOOL) {
            return null;
        }
        String text = message.getText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Map<String, String> row = new HashMap<>(2);
        row.put("type", type.name());
        row.put("content", text);
        return row;
    }

    /**
     * Redis 行 → Message。兼容字段 {@code type} 与旧字段 {@code role}；未知类型丢弃。
     */
    private static Message toMessage(Map<String, String> row) {
        if (row == null) {
            return null;
        }
        String content = row.get("content");
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String type = row.get("type");
        if (!StringUtils.hasText(type)) {
            type = row.get("role");
        }
        if (!StringUtils.hasText(type)) {
            return null;
        }
        return switch (type.trim().toUpperCase()) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> null;
        };
    }
}
