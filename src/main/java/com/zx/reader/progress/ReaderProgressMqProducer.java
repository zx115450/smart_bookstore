package com.zx.reader.progress;

import com.zx.reader.config.ReaderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 投递进度合并落库延迟消息（滑动窗口：到期再判 lastActive）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReaderProgressMqProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ReaderProperties readerProperties;

    public void scheduleFlush(Long userId, Long ebookId) {
        long delayMs = Math.max(1000L, readerProperties.getProgress().getDelayMs());
        ProgressFlushMessage message = new ProgressFlushMessage(userId, ebookId);
        rabbitTemplate.convertAndSend(
                ReaderProgressMqConfig.DELAYED_EXCHANGE,
                ReaderProgressMqConfig.FLUSH_ROUTING_KEY,
                message,
                msg -> {
                    msg.getMessageProperties().setHeader("x-delay", delayMs);
                    return msg;
                }
        );
        log.debug("scheduled progress flush userId={} ebookId={} delayMs={}", userId, ebookId, delayMs);
    }
}
