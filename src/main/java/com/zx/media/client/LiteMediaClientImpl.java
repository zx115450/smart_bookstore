package com.zx.media.client;

import com.zx.media.client.dto.AbortMultipartRequest;
import com.zx.media.client.dto.ChaptersResult;
import com.zx.media.client.dto.CommitMediaRequest;
import com.zx.media.client.dto.CompleteMultipartRequest;
import com.zx.media.client.dto.MediaInfo;
import com.zx.media.client.dto.MultipartUploadRequest;
import com.zx.media.client.dto.MultipartUploadSignature;
import com.zx.media.client.dto.ObjectUrlResponse;
import com.zx.media.client.dto.PlaySignature;
import com.zx.media.client.dto.UploadSignature;
import com.zx.reader.ReaderException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 真实媒资 HTTP 客户端。
 * <p>
 * upload / commit / object-url / play-url → {@code /internal/medias/**} + {@code X-Internal-Token}；
 * listChapters / getMedia → {@code /vod/**}。
 */
public class LiteMediaClientImpl implements LiteMediaClient {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final LiteMediaProperties properties;

    public LiteMediaClientImpl(RestClient restClient, LiteMediaProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public UploadSignature createUploadSignature(String assetType) {
        String type = assetType == null || assetType.isBlank() ? "DOCUMENT" : assetType;
        return getInternal("/internal/medias/upload-signature", UploadSignature.class, type);
    }

    @Override
    public MultipartUploadSignature createMultipartUploadSignature(MultipartUploadRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/medias/upload-signature/multipart")
                    .header(INTERNAL_TOKEN_HEADER, token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 501, (req, res) -> {
                        throw ReaderException.mediaUnavailable("媒资未启用 multipart 上传（501）");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "申请分片上传凭证失败");
                    })
                    .body(MultipartUploadSignature.class);
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    @Override
    public void completeMultipart(String fileId, CompleteMultipartRequest request) {
        try {
            restClient.post()
                    .uri("/internal/medias/uploads/{fileId}/complete", fileId)
                    .header(INTERNAL_TOKEN_HEADER, token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "完成分片合并失败");
                    })
                    .toBodilessEntity();
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    @Override
    public void abortMultipart(String fileId, AbortMultipartRequest request) {
        try {
            restClient.post()
                    .uri("/internal/medias/uploads/{fileId}/abort", fileId)
                    .header(INTERNAL_TOKEN_HEADER, token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "中止分片上传失败");
                    })
                    .toBodilessEntity();
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    @Override
    public MediaInfo commit(CommitMediaRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/medias")
                    .header(INTERNAL_TOKEN_HEADER, token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "commit 失败");
                    })
                    .body(MediaInfo.class);
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    @Override
    public ChaptersResult listChapters(String sourceFileId) {
        try {
            return restClient.get()
                    .uri("/vod/medias/{id}/chapters", sourceFileId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "拉取章目录失败");
                    })
                    .body(ChaptersResult.class);
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    @Override
    public MediaInfo getMedia(String fileId) {
        try {
            return restClient.get()
                    .uri("/vod/medias/{id}", fileId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw ReaderException.chapterNotReady("媒资不存在: " + fileId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "查询媒资失败");
                    })
                    .body(MediaInfo.class);
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    @Override
    public String fetchObjectText(String fileId) {
        ObjectUrlResponse signed = objectUrl(fileId);
        if (signed == null || signed.objectUrl() == null || signed.objectUrl().isBlank()) {
            throw ReaderException.chapterNotReady("未返回 objectUrl: " + fileId);
        }
        try {
            byte[] bytes = restClient.get()
                    .uri(URI.create(signed.objectUrl()))
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw ReaderException.chapterNotReady("章对象不存在: " + fileId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "拉取章正文失败");
                    })
                    .body(byte[].class);
            if (bytes == null) {
                throw ReaderException.chapterNotReady("章正文为空: " + fileId);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    @Override
    public PlaySignature getPlaySignature(String fileId, boolean preview) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/medias/{id}/play-url")
                            .queryParam("preview", preview)
                            .build(fileId))
                    .header(INTERNAL_TOKEN_HEADER, token())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "播放签名失败");
                    })
                    .body(PlaySignature.class);
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    private ObjectUrlResponse objectUrl(String fileId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/medias/{id}/object-url")
                            .queryParam("ttl", properties.getObjectUrlTtlSeconds())
                            .build(fileId))
                    .header(INTERNAL_TOKEN_HEADER, token())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw ReaderException.chapterNotReady("媒资不存在: " + fileId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "签发 object-url 失败");
                    })
                    .body(ObjectUrlResponse.class);
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    private <T> T getInternal(String path, Class<T> type, String assetType) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("assetType", assetType)
                            .build())
                    .header(INTERNAL_TOKEN_HEADER, token())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw mapHttpError(res.getStatusCode().value(), "申请上传凭证失败");
                    })
                    .body(type);
        } catch (ReaderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw mapTransportError(ex);
        }
    }

    private String token() {
        String token = properties.getInternalToken();
        return token == null ? "" : token;
    }

    private static ReaderException mapHttpError(int status, String action) {
        if (status == 401 || status == 403) {
            return ReaderException.mediaUnavailable(action + "：鉴权失败（" + status + "）");
        }
        if (status == 404) {
            return ReaderException.chapterNotReady(action + "：资源不存在");
        }
        if (status == 409) {
            return ReaderException.mediaUnavailable(action + "：状态冲突（409）");
        }
        return ReaderException.mediaUnavailable(action + "：HTTP " + status);
    }

    private static ReaderException mapTransportError(RestClientException ex) {
        if (ex instanceof RestClientResponseException responseEx) {
            return mapHttpError(responseEx.getStatusCode().value(), "媒资调用失败");
        }
        return ReaderException.mediaUnavailable("媒资调用超时或不可达");
    }
}
