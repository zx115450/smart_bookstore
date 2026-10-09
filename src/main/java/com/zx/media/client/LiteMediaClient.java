package com.zx.media.client;

import com.zx.media.client.dto.AbortMultipartRequest;
import com.zx.media.client.dto.ChaptersResult;
import com.zx.media.client.dto.CommitMediaRequest;
import com.zx.media.client.dto.CompleteMultipartRequest;
import com.zx.media.client.dto.MediaInfo;
import com.zx.media.client.dto.MultipartUploadRequest;
import com.zx.media.client.dto.MultipartUploadSignature;
import com.zx.media.client.dto.PlaySignature;
import com.zx.media.client.dto.UploadSignature;

/**
 * 书城 ↔ Lite VOD 媒资客户端。
 * <p>
 * 生产路径：upload / multipart / commit / object-url / play-url 走 {@code /internal/medias/**} + {@code X-Internal-Token}；
 * 元数据（详情 / 章目录）走公开 {@code /vod/**}（书城侧先完成用户鉴权）。
 */
public interface LiteMediaClient {

    /** 申请上传预签名（生产：internal）。 */
    UploadSignature createUploadSignature(String assetType);

    /** 申请 multipart 分片上传凭证（生产：internal）。 */
    MultipartUploadSignature createMultipartUploadSignature(MultipartUploadRequest request);

    /** 完成分片合并；成功后才能 commit。 */
    void completeMultipart(String fileId, CompleteMultipartRequest request);

    /** 中止分片上传。 */
    void abortMultipart(String fileId, AbortMultipartRequest request);

    /** 直传完成后 commit（生产：internal）。 */
    MediaInfo commit(CommitMediaRequest request);

    /** 拉取 DOCUMENT 子章目录。 */
    ChaptersResult listChapters(String sourceFileId);

    /** 媒资详情（轮询切章 / 转码状态）。 */
    MediaInfo getMedia(String fileId);

    /**
     * 读章正文：先拿 object-url（internal + Token），再 GET MinIO 短链。
     */
    String fetchObjectText(String fileId);

    /**
     * 视频播放签名（B6）：书城鉴权后走 internal play-url + Token。
     */
    PlaySignature getPlaySignature(String fileId, boolean preview);
}
