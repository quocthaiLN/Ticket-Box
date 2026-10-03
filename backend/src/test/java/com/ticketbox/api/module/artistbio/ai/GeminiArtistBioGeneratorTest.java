package com.ticketbox.api.module.artistbio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.artistbio.domain.exception.ArtistBioProcessingException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiArtistBioGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GeminiArtistBioGenerator generator = new GeminiArtistBioGenerator(mapper, "test-key", "gemini-2.5-flash", builder.build());

    @Test void sendsStructuredPromptAndParsesBio() throws Exception {
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("responseSchema")))
                .andRespond(withSuccess(response("A draft biography."), MediaType.APPLICATION_JSON));
        assertThat(generator.generate("source text")).isEqualTo("A draft biography.");
        server.verify();
    }
    @ParameterizedTest @ValueSource(ints = {429, 500, 503})
    void retriesTransientErrors(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)).body("secret upstream details"));
        assertFailure(true);
    }
    @ParameterizedTest @ValueSource(ints = {400, 401, 403})
    void rejectsPermanentErrors(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)).body("secret upstream details"));
        assertFailure(false);
    }
    @Test void retriesTimeout() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException()));
        assertFailure(true);
    }
    @Test void rejectsBlockedAndInvalidOutput() throws Exception {
        for (String body : new String[] {"{}", "null", "not json", response(""), response("<script>alert(1)</script>"),
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"}) {
            server.reset(); server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertFailure(false);
        }
    }
    @Test void missingKeyDoesNotMakeHttpRequest() {
        var missing = new GeminiArtistBioGenerator(mapper, "", "model", builder.build());
        assertThatThrownBy(() -> missing.generate("text")).isInstanceOf(ArtistBioProcessingException.class);
        server.verify();
    }
    private void assertFailure(boolean retryable) {
        assertThatThrownBy(() -> generator.generate("text")).isInstanceOf(ArtistBioProcessingException.class)
                .hasMessageNotContaining("secret").extracting("retryable").isEqualTo(retryable);
    }
    private String response(String bio) throws Exception {
        return mapper.writeValueAsString(Map.of("candidates", java.util.List.of(Map.of("finishReason", "STOP",
                "content", Map.of("parts", java.util.List.of(Map.of("text", mapper.writeValueAsString(Map.of("bio", bio)))))))));
    }
}
