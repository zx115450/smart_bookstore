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
import com.zx.reader.ReaderException;

/**
 * {@code bookstore.media.lite-vod.enabled=false} 时的占位实现：调用即抛 6002。
 */
public class DisabledLiteMediaClient implements LiteMediaClient {

    @Override
    public UploadSignature createUploadSignature(String assetType) {
        throw ReaderException.mediaDisabled();
    }

    @Override
    public MultipartUploadSignature createMultipartUploadSignature(MultipartUploadRequest request) {
        throw ReaderException.mediaDisabled();
    }

    @Override
    public void completeMultipart(String fileId, CompleteMultipartRequest request) {
        throw ReaderException.mediaDisabled();
    }

    @Override
    public void abortMultipart(String fileId, AbortMultipartRequest request) {
        throw ReaderException.mediaDisabled();
    }

    @Override
    public MediaInfo commit(CommitMediaRequest request) {
        throw ReaderException.mediaDisabled();
    }

    @Override
    public ChaptersResult listChapters(String sourceFileId) {
        throw ReaderException.mediaDisabled();
    }

    @Override
    public MediaInfo getMedia(String fileId) {
        throw ReaderException.mediaDisabled();
    }

    @Override
    public String fetchObjectText(String fileId) {
        throw ReaderException.mediaDisabled();
    }

    @Override
    public PlaySignature getPlaySignature(String fileId, boolean preview) {
        throw ReaderException.mediaDisabled();
    }
}
