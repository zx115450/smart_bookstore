package com.zx.media.client;

import com.zx.media.client.dto.AbortMultipartRequest;
import com.zx.media.client.dto.ChapterInfo;
import com.zx.media.client.dto.ChaptersResult;
import com.zx.media.client.dto.CommitMediaRequest;
import com.zx.media.client.dto.CompleteMultipartRequest;
import com.zx.media.client.dto.MediaInfo;
import com.zx.media.client.dto.MultipartUploadRequest;
import com.zx.media.client.dto.MultipartUploadSignature;
import com.zx.media.client.dto.PartUrl;
import com.zx.media.client.dto.PlaySignature;
import com.zx.media.client.dto.UploadSignature;
import com.zx.reader.ReaderException;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock 媒资客户端：固定三章样例 + 样例 VIDEO，不访问 vod-api。
 */
public class MockLiteMediaClient implements LiteMediaClient {

    public static final String SOURCE_FILE_ID = "mock-doc-1";
    public static final String CHAPTER_1 = "mock-c-1";
    public static final String CHAPTER_2 = "mock-c-2";
    public static final String CHAPTER_3 = "mock-c-3";
    /** B6 配套视频样例。 */
    public static final String VIDEO_FILE_ID = "mock-video-1";

    private static final List<ChapterInfo> CHAPTERS = List.of(
            new ChapterInfo(1, "持久化", CHAPTER_1, 120, "CHAPTER"),
            new ChapterInfo(2, "主从复制", CHAPTER_2, 150, "CHAPTER"),
            new ChapterInfo(3, "哨兵模式", CHAPTER_3, 180, "CHAPTER")
    );

    @Override
    public UploadSignature createUploadSignature(String assetType) {
        long expireAt = System.currentTimeMillis() / 1000 + 3600;
        return new UploadSignature(
                SOURCE_FILE_ID,
                "http://localhost/mock-upload/" + SOURCE_FILE_ID,
                "raw/" + SOURCE_FILE_ID + "/source.bin",
                expireAt);
    }

    @Override
    public MultipartUploadSignature createMultipartUploadSignature(MultipartUploadRequest request) {
        long partSize = request == null || request.partSize() <= 0 ? 5 * 1024 * 1024L : request.partSize();
        long contentLength = request == null || request.contentLength() <= 0 ? partSize : request.contentLength();
        int partCount = (int) ((contentLength + partSize - 1) / partSize);
        if (partCount < 1) {
            partCount = 1;
        }
        List<PartUrl> parts = new ArrayList<>(partCount);
        for (int i = 1; i <= partCount; i++) {
            parts.add(new PartUrl(i, "http://localhost/mock-upload/" + SOURCE_FILE_ID + "/part/" + i));
        }
        long expireAt = System.currentTimeMillis() / 1000 + 3600;
        return new MultipartUploadSignature(
                SOURCE_FILE_ID,
                "raw/" + SOURCE_FILE_ID + "/source.bin",
                "mock-upload-id",
                partSize,
                partCount,
                parts,
                expireAt);
    }

    @Override
    public void completeMultipart(String fileId, CompleteMultipartRequest request) {
        // no-op
    }

    @Override
    public void abortMultipart(String fileId, AbortMultipartRequest request) {
        // no-op
    }

    @Override
    public MediaInfo commit(CommitMediaRequest request) {
        return finishedDocument(request == null ? SOURCE_FILE_ID : request.fileId(),
                request == null ? "mock.md" : request.filename());
    }

    @Override
    public ChaptersResult listChapters(String sourceFileId) {
        String id = sourceFileId == null || sourceFileId.isBlank() ? SOURCE_FILE_ID : sourceFileId;
        return new ChaptersResult(id, CHAPTERS);
    }

    @Override
    public MediaInfo getMedia(String fileId) {
        if (isMockVideo(fileId)) {
            return new MediaInfo(
                    fileId,
                    "VIDEO",
                    "intro.mp4",
                    "video/mp4",
                    null,
                    null,
                    "FINISHED",
                    "已完成",
                    null);
        }
        if (CHAPTER_1.equals(fileId) || CHAPTER_2.equals(fileId) || CHAPTER_3.equals(fileId)) {
            ChapterInfo chapter = CHAPTERS.stream()
                    .filter(c -> c.fileId().equals(fileId))
                    .findFirst()
                    .orElseThrow();
            return new MediaInfo(
                    fileId,
                    "CHAPTER",
                    chapter.title() + ".md",
                    "text/markdown",
                    SOURCE_FILE_ID,
                    chapter.chapterNo(),
                    "FINISHED",
                    "已完成",
                    null);
        }
        return finishedDocument(fileId == null ? SOURCE_FILE_ID : fileId, "redis-preview.md");
    }

    @Override
    public String fetchObjectText(String fileId) {
        if (fileId != null && fileId.matches("demo-e\\d+-c[123]")) {
            return demoChapterText(fileId.charAt(fileId.length() - 1) - '0');
        }
        String resource = switch (fileId) {
            case CHAPTER_1 -> "reader/mock/chapter-1.md";
            case CHAPTER_2 -> "reader/mock/chapter-2.md";
            case CHAPTER_3 -> "reader/mock/chapter-3.md";
            default -> throw ReaderException.chapterNotReady("未知章 fileId: " + fileId);
        };
        return readClasspath(resource);
    }

    /** V4 种子电子书短正文（不落库，仅 Mock 返回）。 */
    private static String demoChapterText(int chapterNo) {
        return switch (chapterNo) {
            case 1 -> """
                    # 开篇导读

                    这是线上试读第一章，篇幅很短，用于演示目录与 BFF 读章。

                    试看章无需借阅即可打开。
                    """;
            case 2 -> """
                    # 要点速览

                    本章同样为试看内容：确认登录态、目录锁定标记与正文拉取是否正常。
                    """;
            case 3 -> """
                    # 进阶片段

                    本章默认非试看。未借阅/未购买时应返回 5103；解锁后可阅读本段短文。
                    """;
            default -> throw ReaderException.chapterNotReady("未知演示章: " + chapterNo);
        };
    }

    @Override
    public PlaySignature getPlaySignature(String fileId, boolean preview) {
        if (fileId == null || fileId.isBlank()) {
            throw ReaderException.chapterNotReady("fileId 为空");
        }
        if ("missing-video".equals(fileId)) {
            throw ReaderException.chapterNotReady("媒资不存在: " + fileId);
        }
        long expireAt = System.currentTimeMillis() / 1000 + 600;
        String playUrl = "http://localhost:8089/play/" + fileId
                + "?preview=" + preview
                + "&sign=mock-sign"
                + "&expire=" + expireAt;
        return new PlaySignature(fileId, playUrl, "mock-sign", expireAt);
    }

    private static boolean isMockVideo(String fileId) {
        return VIDEO_FILE_ID.equals(fileId) || (fileId != null && fileId.startsWith("mock-video"));
    }

    private static MediaInfo finishedDocument(String fileId, String filename) {
        return new MediaInfo(
                fileId,
                "DOCUMENT",
                filename,
                "text/markdown",
                null,
                null,
                "FINISHED",
                "已完成",
                null);
    }

    private static String readClasspath(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw ReaderException.mediaUnavailable("无法读取 Mock 章正文: " + path);
        }
    }
}
