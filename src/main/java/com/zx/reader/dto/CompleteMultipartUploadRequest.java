package com.zx.reader.dto;

import com.zx.media.client.dto.PartEtag;

import java.util.List;

/**
 * 管理端完成分片合并。
 */
public class CompleteMultipartUploadRequest {

    private String uploadId;
    private List<PartEtag> parts;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public List<PartEtag> getParts() {
        return parts;
    }

    public void setParts(List<PartEtag> parts) {
        this.parts = parts;
    }
}
