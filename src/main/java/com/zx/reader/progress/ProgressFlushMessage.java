package com.zx.reader.progress;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RabbitMQ 延迟合并落库消息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressFlushMessage {

    private Long userId;
    private Long ebookId;
}
