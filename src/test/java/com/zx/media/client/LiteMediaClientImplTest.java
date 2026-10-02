package com.zx.media.client;

import com.zx.media.client.dto.CommitMediaRequest;
import com.zx.media.client.dto.UploadSignature;
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
}
