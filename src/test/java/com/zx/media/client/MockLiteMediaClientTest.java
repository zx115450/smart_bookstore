package com.zx.media.client;

import com.zx.media.client.dto.ChaptersResult;
import com.zx.media.client.dto.CommitMediaRequest;
import com.zx.media.client.dto.MediaInfo;
import com.zx.media.client.dto.PlaySignature;
import com.zx.media.client.dto.UploadSignature;
import com.zx.reader.ReaderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLiteMediaClientTest {

    private final MockLiteMediaClient client = new MockLiteMediaClient();

    @Test
    void listChapters_shouldReturnThreeChapters() {
        ChaptersResult result = client.listChapters(MockLiteMediaClient.SOURCE_FILE_ID);

        assertEquals(MockLiteMediaClient.SOURCE_FILE_ID, result.sourceFileId());
        assertEquals(3, result.chapters().size());
        assertEquals(MockLiteMediaClient.CHAPTER_1, result.chapters().get(0).fileId());
        assertEquals(MockLiteMediaClient.CHAPTER_2, result.chapters().get(1).fileId());
        assertEquals(MockLiteMediaClient.CHAPTER_3, result.chapters().get(2).fileId());
        assertEquals(1, result.chapters().get(0).chapterNo());
        assertEquals(3, result.chapters().get(2).chapterNo());
    }

    @Test
    void createUploadSignature_shouldReturnMockDocId() {
        UploadSignature sig = client.createUploadSignature("DOCUMENT");
        assertEquals(MockLiteMediaClient.SOURCE_FILE_ID, sig.fileId());
        assertTrue(sig.uploadUrl().contains("mock-upload"));
    }

    @Test
    void commit_shouldSucceed() {
        MediaInfo info = client.commit(CommitMediaRequest.document(
                MockLiteMediaClient.SOURCE_FILE_ID, "redis.md", "MARKDOWN"));
        assertEquals("DOCUMENT", info.assetType());
        assertEquals("FINISHED", info.status());
    }

    @Test
    void getMedia_shouldReturnFinishedDocument() {
        MediaInfo info = client.getMedia(MockLiteMediaClient.SOURCE_FILE_ID);
        assertEquals("DOCUMENT", info.assetType());
        assertEquals("FINISHED", info.status());
    }

    @Test
    void fetchObjectText_shouldLoadClasspathBodies() {
        String c1 = client.fetchObjectText(MockLiteMediaClient.CHAPTER_1);
        String c2 = client.fetchObjectText(MockLiteMediaClient.CHAPTER_2);
        String c3 = client.fetchObjectText(MockLiteMediaClient.CHAPTER_3);

        assertTrue(c1.contains("持久化"));
        assertTrue(c2.contains("主从复制"));
        assertTrue(c3.contains("哨兵"));
    }

    @Test
    void getPlaySignature_shouldReturnFakeUrl() {
        PlaySignature play = client.getPlaySignature("vid-1", true);
        assertEquals("vid-1", play.fileId());
        assertTrue(play.playUrl().contains("preview=true"));
    }
}

class DisabledLiteMediaClientTest {

    @Test
    void shouldThrowMediaDisabled() {
        DisabledLiteMediaClient client = new DisabledLiteMediaClient();
        ReaderException ex = assertThrows(ReaderException.class,
                () -> client.listChapters("any"));
        assertEquals(6002, ex.getCode());
        assertTrue(ex.getMessage().contains("未启用"));
    }
}
