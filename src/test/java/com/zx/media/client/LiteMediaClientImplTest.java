package com.zx.media.client;

import com.zx.media.client.dto.AbortMultipartRequest;
import com.zx.media.client.dto.CommitMediaRequest;
import com.zx.media.client.dto.CompleteMultipartRequest;
import com.zx.media.client.dto.MultipartUploadRequest;
import com.zx.media.client.dto.MultipartUploadSignature;
import com.zx.media.client.dto.PartEtag;
import com.zx.media.client.dto.PlaySignature;
import com.zx.media.client.dto.UploadSignature;

import java.util.List;
import com.zx.reader.ReaderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class LiteMediaClientImplTest {

    private LiteMediaProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private LiteMediaClientImpl client;

    @BeforeEach
    void setUp() {
        properties = new LiteMediaProperties();
        properties.setEnabled(true);
        properties.setMock(false);
        properties.setBaseUrl("http://vod.test");
        properties.setInternalToken("secret-token");
        properties.setObjectUrlTtlSeconds(60);

        builder = RestClient.builder().baseUrl("http://vod.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LiteMediaClientImpl(builder.build(), properties);
    }

    @Test
    void createUploadSignature_shouldCallInternalWithToken() {
        server.expect(requestTo("http://vod.test/internal/medias/upload-signature?assetType=DOCUMENT"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, "secret-token"))
                .andRespond(withSuccess("""
                        {"fileId":"doc-1","uploadUrl":"http://minio/put","objectKey":"raw/doc-1/source.bin","expireAt":1710000000}
                        """, MediaType.APPLICATION_JSON));

        UploadSignature sig = client.createUploadSignature("DOCUMENT");

        assertEquals("doc-1", sig.fileId());
        assertEquals("http://minio/put", sig.uploadUrl());
        server.verify();
    }

    @Test
    void createUploadSignature_shouldMap401ToMediaUnavailable() {
        server.expect(requestTo("http://vod.test/internal/medias/upload-signature?assetType=DOCUMENT"))
                .andExpect(header(LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, "secret-token"))
                .andRespond(withStatus(UNAUTHORIZED));

        ReaderException ex = assertThrows(ReaderException.class,
                () -> client.createUploadSignature("DOCUMENT"));

        assertEquals(6002, ex.getCode());
        assertTrue(ex.getMessage().contains("鉴权失败"));
        server.verify();
    }

    @Test
    void createMultipartUploadSignature_shouldPostInternalWithToken() {
        server.expect(requestTo("http://vod.test/internal/medias/upload-signature/multipart"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, "secret-token"))
                .andRespond(withSuccess("""
                        {"fileId":"doc-m","objectKey":"raw/doc-m/source.bin","uploadId":"uid-1",
                         "partSize":10485760,"partCount":2,
                         "parts":[{"partNumber":1,"uploadUrl":"http://minio/p1"},
                                  {"partNumber":2,"uploadUrl":"http://minio/p2"}],
                         "expireAt":1710000000}
                        """, MediaType.APPLICATION_JSON));

        MultipartUploadSignature sig = client.createMultipartUploadSignature(
                MultipartUploadRequest.document("a.md", "text/markdown", 20_000_000L, 10_485_760L));
        assertEquals("doc-m", sig.fileId());
        assertEquals("uid-1", sig.uploadId());
        assertEquals(2, sig.partCount());
        server.verify();
    }

    @Test
    void completeMultipart_shouldPostInternalWithToken() {
        server.expect(requestTo("http://vod.test/internal/medias/uploads/doc-m/complete"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, "secret-token"))
                .andRespond(withSuccess());

        client.completeMultipart("doc-m",
                new CompleteMultipartRequest("uid-1", List.of(new PartEtag(1, "\"a\""))));
        server.verify();
    }

    @Test
    void abortMultipart_shouldPostInternalWithToken() {
        server.expect(requestTo("http://vod.test/internal/medias/uploads/doc-m/abort"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, "secret-token"))
                .andRespond(withSuccess());

        client.abortMultipart("doc-m", new AbortMultipartRequest("uid-1"));
        server.verify();
    }

    @Test
    void commit_shouldPostInternalWithToken() {
        server.expect(requestTo("http://vod.test/internal/medias"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, "secret-token"))
                .andRespond(withSuccess("""
                        {"fileId":"doc-1","assetType":"DOCUMENT","filename":"a.md","status":"PROCESSING","statusText":"处理中"}
                        """, MediaType.APPLICATION_JSON));

        var info = client.commit(CommitMediaRequest.document("doc-1", "a.md", "MARKDOWN"));
        assertEquals("PROCESSING", info.status());
        server.verify();
    }

    @Test
    void fetchObjectText_shouldUseObjectUrlThenGetBody() {
        server.expect(requestTo("http://vod.test/internal/medias/mock-c-1/object-url?ttl=60"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, "secret-token"))
                .andRespond(withSuccess("""
                        {"fileId":"mock-c-1","objectUrl":"http://vod.test/minio/chap-1","objectKey":"chap/x/001.md","assetType":"CHAPTER","expireAt":1710000000}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://vod.test/minio/chap-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("# hello chapter", MediaType.TEXT_PLAIN));

        String text = client.fetchObjectText("mock-c-1");
        assertEquals("# hello chapter", text);
        server.verify();
    }

    @Test
    void listChapters_shouldCallPublicVod() {
        server.expect(requestTo("http://vod.test/vod/medias/doc-1/chapters"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"sourceFileId":"doc-1","chapters":[{"chapterNo":1,"title":"A","fileId":"c1","wordCount":10,"assetType":"CHAPTER"}]}
                        """, MediaType.APPLICATION_JSON));

        var result = client.listChapters("doc-1");
        assertEquals(1, result.chapters().size());
        assertEquals("c1", result.chapters().getFirst().fileId());
        server.verify();
    }

    @Test
    void getPlaySignature_shouldCallInternalPlayUrlWithToken() {
        server.expect(requestTo("http://vod.test/internal/medias/vid-1/play-url?preview=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, "secret-token"))
                .andRespond(withSuccess("""
                        {"fileId":"vid-1","playUrl":"http://cdn/hls/vid-1/index.m3u8?e=1&exper=300&sign=abc","signature":"abc","expireAt":1710000000}
                        """, MediaType.APPLICATION_JSON));

        PlaySignature play = client.getPlaySignature("vid-1", true);
        assertEquals("vid-1", play.fileId());
        assertTrue(play.playUrl().contains("exper=300"));
        assertEquals("abc", play.signature());
        server.verify();
    }
}
