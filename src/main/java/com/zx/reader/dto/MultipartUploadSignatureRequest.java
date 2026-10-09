package com.zx.reader.dto;

/**
 * 管理端申请 DOCUMENT multipart 凭证。
 */
public class MultipartUploadSignatureRequest {

    private String filename;
    private String contentType;
    private Long contentLength;
    /** 每片字节数；缺省由媒资侧默认（建议 ≥ 5MiB）。 */
    private Long partSize;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    public Long getPartSize() {
        return partSize;
    }

    public void setPartSize(Long partSize) {
        this.partSize = partSize;
    }
}
