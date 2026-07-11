package com.zx.bookstore.borrow.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 借阅到期 Redis ZSET：confirm 写入 due_at，Lua 原子弹出到期 orderId，return 时移除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowDueRedisService {

    static final String DUE_ZSET_KEY = "borrow:due:zset";

    private final StringRedisTemplate redis;

    @SuppressWarnings("rawtypes")
    private DefaultRedisScript<List> popDueScript;

    @PostConstruct
    void initScript() {
        popDueScript = new DefaultRedisScript<>();
        popDueScript.setResultType(List.class);
        popDueScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/borrow_due_pop.lua")));
    }

    /** 确认借出后写入 ZSET，score 为 due_at 的 Unix 秒。 */
    public void scheduleDue(Long orderId, LocalDateTime dueAt) {
        if (orderId == null || dueAt == null) {
            return;
        }
        double score = dueAt.atZone(ZoneId.systemDefault()).toEpochSecond();
        redis.opsForZSet().add(DUE_ZSET_KEY, String.valueOf(orderId), score);
    }

    /** 还书后从 ZSET 移除（幂等）。 */
    public void removeDue(Long orderId) {
        if (orderId == null) {
            return;
        }
        redis.opsForZSet().remove(DUE_ZSET_KEY, String.valueOf(orderId));
    }

    /**
     * Lua 原子弹出 score &lt;= nowEpoch 的 orderId 并删除。
     *
     * @return 弹出的借阅单 id 列表，无则空列表
     */
    @SuppressWarnings("unchecked")
    public List<Long> popDueOrderIds(long nowEpoch, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> raw = redis.execute(
                popDueScript,
                List.of(DUE_ZSET_KEY),
                String.valueOf(nowEpoch),
                String.valueOf(limit)
        );
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = new ArrayList<>(raw.size());
        for (String member : raw) {
            try {
                orderIds.add(Long.parseLong(member));
            } catch (NumberFormatException e) {
                log.warn("invalid borrow due zset member, value={}", member);
            }
        }
        return Collections.unmodifiableList(orderIds);
    }
}
