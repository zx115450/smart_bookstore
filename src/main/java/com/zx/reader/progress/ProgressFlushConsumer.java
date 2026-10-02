package com.zx.reader.progress;

import com.rabbitmq.client.Channel;
import com.zx.reader.service.ReadingProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 进度延迟合并落库消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressFlushConsumer {

    private final ReadingProgressService readingProgressService;

    @RabbitListener(queues = ReaderProgressMqConfig.FLUSH_QUEUE)
    public void consume(ProgressFlushMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            readingProgressService.onFlushDue(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("progress flush failed userId={} ebookId={}",
                    message == null ? null : message.getUserId(),
                    message == null ? null : message.getEbookId(), e);
            // 失败重回队列可能打爆；MVP Nack 不 requeue，依赖下次心跳再调度
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
