package com.zx.media.client.dto;

import java.util.List;

/** 完成分片合并。 */
public record CompleteMultipartRequest(String uploadId, List<PartEtag> parts) {
}
