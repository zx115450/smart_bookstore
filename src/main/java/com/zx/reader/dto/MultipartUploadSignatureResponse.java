package com.zx.reader.dto;

import com.zx.media.client.dto.PartUrl;

import java.util.List;

/**
 * 管理端 DOCUMENT multipart 凭证响应。
 */
public class MultipartUploadSignatureResponse {

    private Long ebookId;
    private String fileId;
    private String objectKey;
    private String uploadId;
    private long partSize;
    private int partCount;
    private List<PartUrl> parts;
    private long expireAt;
    private String assetType = "DOCUMENT";

    public Long getEbookId() {
        return ebookId;
    }

    public void setEbookId(Long ebookId) {
        this.ebookId = ebookId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public long getPartSize() {
        return partSize;
    }

    public void setPartSize(long partSize) {
        this.partSize = partSize;
    }

    public int getPartCount() {
        return partCount;
    }

    public void setPartCount(int partCount) {
        this.partCount = partCount;
    }

    public List<PartUrl> getParts() {
        return parts;
    }

    public void setParts(List<PartUrl> parts) {
        this.parts = parts;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }
}
