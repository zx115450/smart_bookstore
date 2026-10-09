package com.zx.reader;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

/**
 * 线上阅读 / 学习 Agent 业务异常（错误码 51xx / 52xx / 53xx / 60xx）。
 */
public class ReaderException extends BusinessException {

    public ReaderException(int code, String message) {
        super(code, message);
    }

    public static ReaderException ebookNotFound() {
        return new ReaderException(ErrorCode.READER_EBOOK_NOT_FOUND, "电子书不存在或已下架");
    }

    public static ReaderException previewDenied() {
        return new ReaderException(ErrorCode.READER_PREVIEW_DENIED, "本章需借阅或购买后阅读");
    }

    /** 需绑定实体书才能走的业务（如借阅解锁）；TOC 同步本身不强制 book_id。 */
    public static ReaderException ebookNotBound() {
        return new ReaderException(ErrorCode.READER_EBOOK_NOT_BOUND, "电子书未关联实体书");
    }

    public static ReaderException noteForbidden() {
        return new ReaderException(ErrorCode.READER_NOTE_FORBIDDEN, "无权操作该笔记");
    }

    public static ReaderException noteQuoteDenied() {
        return new ReaderException(ErrorCode.READER_NOTE_QUOTE_DENIED, "锁定章节划线过长，无法保存");
    }

    public static ReaderException noteContentDenied() {
        return new ReaderException(ErrorCode.READER_NOTE_QUOTE_DENIED, "锁定章节笔记过长，无法保存");
    }

    public static ReaderException noteNotFound() {
        return new ReaderException(ErrorCode.READER_NOTE_FORBIDDEN, "笔记不存在");
    }

    public static ReaderException agentRateLimited() {
        return new ReaderException(ErrorCode.READER_AGENT_RATE_LIMITED, "学习助手调用过于频繁，请稍后再试");
    }

    /** 媒资未启用、超时、5xx、Token 失败等。 */
    public static ReaderException mediaUnavailable(String message) {
        return new ReaderException(
                ErrorCode.MEDIA_UNAVAILABLE,
                message == null || message.isBlank() ? "媒资服务暂时不可用" : message);
    }

    public static ReaderException mediaDisabled() {
        return mediaUnavailable("媒资未启用");
    }

    /** 章对象不存在或切章未完成。 */
    public static ReaderException chapterNotReady(String message) {
        return new ReaderException(
                ErrorCode.MEDIA_CHAPTER_NOT_READY,
                message == null || message.isBlank() ? "章节内容不存在或尚未准备好" : message);
    }

    /** 图书配套视频绑定不存在。 */
    public static ReaderException mediaRefNotFound() {
        return new ReaderException(ErrorCode.MEDIA_REF_NOT_FOUND, "图书视频绑定不存在");
    }

    /** fileId 在媒资侧不存在或未处理完成。 */
    public static ReaderException mediaNotReady(String message) {
        return new ReaderException(
                ErrorCode.MEDIA_CHAPTER_NOT_READY,
                message == null || message.isBlank() ? "媒资不存在或尚未处理完成" : message);
    }

    /** 无完整播放权且不允许试看。 */
    public static ReaderException mediaPlayDenied() {
        return new ReaderException(ErrorCode.MEDIA_PLAY_DENIED, "该视频需借阅或购买后观看");
    }

    /** 播放签名申请失败。 */
    public static ReaderException playSignFailed(String message) {
        return new ReaderException(
                ErrorCode.MEDIA_PLAY_SIGN_FAILED,
                message == null || message.isBlank() ? "播放签名申请失败" : message);
    }
}
