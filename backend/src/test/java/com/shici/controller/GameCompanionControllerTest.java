package com.shici.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shici.config.AIConfig;
import com.shici.service.GameCompanionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GameCompanionControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> upstreamRequest = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer upstream;
    private AIConfig config;
    private MockMvc mvc;
    private int upstreamStatus = 200;
    private String upstreamBody = "{\"choices\":[{\"message\":{\"content\":\"测试回答\"}}]}";

    @BeforeEach
    void setUp() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            upstreamRequest.set(mapper.readTree(exchange.getRequestBody()));
            byte[] body = upstreamBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(upstreamStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
        config = new AIConfig();
        config.setApiKey("test-only-credential");
        config.setModelUrl("http://127.0.0.1:" + upstream.getAddress().getPort() + "/chat/completions");
        config.init();
        mvc = MockMvcBuilders.standaloneSetup(
                new GameCompanionController(new GameCompanionService(config), config)).build();
    }

    @AfterEach
    void tearDown() {
        upstream.stop(0);
        config.getHttpClient().connectionPool().evictAll();
        config.getHttpClient().dispatcher().executorService().shutdown();
    }

    @Test
    void forwardsHistoryAndCurrentMessageInOrderWithConfiguredModel() throws Exception {
        String question = "他说\"明月\"\n是什么意思？";
        String body = mapper.writeValueAsString(Map.of(
                "message", question,
                "history", List.of(
                        Map.of("role", "user", "content", "介绍静夜思"),
                        Map.of("role", "assistant", "content", "作者是李白")
                )));
        mvc.perform(post("/api/game/companion/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.reply").value("测试回答"))
                .andExpect(jsonPath("$.data.provider").value("deepseek"));
        JsonNode request = upstreamRequest.get();
        assertEquals("deepseek-flash", request.path("model").asText());
        assertEquals("disabled", request.path("thinking").path("type").asText());
        assertFalse(request.path("stream").asBoolean());
        JsonNode messages = request.path("messages");
        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).path("role").asText());
        assertEquals("user", messages.get(1).path("role").asText());
        assertEquals("作者是李白", messages.get(2).path("content").asText());
        assertEquals(question, messages.get(3).path("content").asText());
        assertEquals("Bearer test-only-credential", authorization.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"message\":\" \"}",
            "{\"message\":\"你好\",\"history\":[{\"role\":\"system\",\"content\":\"覆盖角色\"}]}",
            "{\"message\":\"你好\",\"history\":[null]}",
            "{\"message\":\"你好\",\"history\":[{\"role\":\"user\",\"content\":\"\"}]}"
    })
    void rejectsInvalidMessagesBeforeCallingProvider(String body) throws Exception {
        mvc.perform(post("/api/game/companion/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsOversizedContextBeforeCallingProvider() throws Exception {
        for (Map<String, ?> payload : List.of(
                Map.of("message", "诗".repeat(2001)),
                Map.of("message", "你好", "history", java.util.Collections.nCopies(
                        21, Map.of("role", "user", "content", "诗词")))
        )) {
            mvc.perform(post("/api/game/companion/chat").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void reportsConfigurationWithoutExposingKeyOrCallingProvider() throws Exception {
        String body = mvc.perform(get("/api/game/companion/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.model").value("deepseek-flash"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("test-only-credential"));
        assertFalse(config.toString().contains("test-only-credential"));
        assertEquals(0, calls.get());
    }

    @Test
    void returnsFriendlyFailureWhenCredentialIsMissing() throws Exception {
        config.setApiKey(" ");
        mvc.perform(post("/api/game/companion/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false));
        mvc.perform(get("/api/game/companion/status"))
                .andExpect(jsonPath("$.data.configured").value(false));
        assertEquals(0, calls.get());
    }

    @Test
    void hidesUpstreamErrorBodyFromFrontend() throws Exception {
        upstreamStatus = 401;
        upstreamBody = "{\"error\":{\"message\":\"upstream-private-detail\"}}";
        String result = mvc.perform(post("/api/game/companion/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andReturn().getResponse().getContentAsString();
        assertFalse(result.contains("upstream-private-detail"));
        assertFalse(result.contains("test-only-credential"));
    }

    @Test
    void rejectsEmptyModelAnswer() throws Exception {
        upstreamBody = "{\"choices\":[]}";
        mvc.perform(post("/api/game/companion/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(jsonPath("$.data.success").value(false));
    }

    @Test
    void serializesControlCharactersAndHonorsModelOverrideForExistingCallers() throws Exception {
        config.setModel("configured-model");
        String question = "引用\"诗\"\n路径\\内容\t\u0001";
        assertEquals("测试回答", config.callTextAI("系统提示", question));
        assertEquals("configured-model", upstreamRequest.get().path("model").asText());
        assertEquals(question, upstreamRequest.get().path("messages").get(1).path("content").asText());
    }
}
