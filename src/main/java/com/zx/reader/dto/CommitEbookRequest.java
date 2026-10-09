package com.zx.reader.dto;

/**
 * 管理端：直传完成后 commit DOCUMENT。
 */
public class CommitEbookRequest {

    private String fileId;
    private String filename;
    /** MARKDOWN / TXT；缺省 MARKDOWN。 */
    private String splitRule;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getSplitRule() {
        return splitRule;
    }

    public void setSplitRule(String splitRule) {
        this.splitRule = splitRule;
    }
}
