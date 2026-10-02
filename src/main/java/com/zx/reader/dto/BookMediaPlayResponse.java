package com.zx.reader.dto;

/**
 * 图书配套视频播放签名（短时 playUrl，密钥不下发浏览器以外用途）。
 */
public class BookMediaPlayResponse {

    private Long refId;
    private String fileId;
    private String playUrl;
    private String signature;
    private long expireAt;
    private boolean preview;
    private Integer previewSeconds;

    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getPlayUrl() { return playUrl; }
    public void setPlayUrl(String playUrl) { this.playUrl = playUrl; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public long getExpireAt() { return expireAt; }
    public void setExpireAt(long expireAt) { this.expireAt = expireAt; }
    public boolean isPreview() { return preview; }
    public void setPreview(boolean preview) { this.preview = preview; }
    public Integer getPreviewSeconds() { return previewSeconds; }
    public void setPreviewSeconds(Integer previewSeconds) { this.previewSeconds = previewSeconds; }
}
