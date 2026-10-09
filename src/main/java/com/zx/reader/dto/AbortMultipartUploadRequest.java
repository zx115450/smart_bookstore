package com.zx.reader.dto;

/**
 * 管理端中止分片上传。
 */
public class AbortMultipartUploadRequest {

    private String uploadId;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }
}
