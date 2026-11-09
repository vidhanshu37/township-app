package com.township.backend.complaints;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmTriageClientTest {

    private static final class Fake implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> lastBody = new AtomicReference<>();
        final AtomicReference<String> lastAuth = new AtomicReference<>();
        final AtomicReference<String> lastPath = new AtomicReference<>();

        Fake(int status, String body, long delayMs) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                calls.incrementAndGet();
                lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
                lastPath.set(ex.getRequestURI().getPath());
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    // test server only
                }
                byte[] out = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(status, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1";
        }

        LlmTriageClient client(long timeoutMs) {
            return new LlmTriageClient(url(), "test-key", "test-model", Duration.ofMillis(timeoutMs));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static String reply(String contentJson) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + LlmTriageClient.jsonString(contentJson) + "}}]}";
    }

    @Test
    void aValidAnswerBecomesAnAiResult() throws Exception {
        try (Fake f = new Fake(200, reply("{\"category\":\"PLUMBING\",\"priority\":\"MEDIUM\",\"reason\":\"Water is leaking from the ceiling.\"}"), 0)) {
            var r = f.client(2000).classify("Paani tapak raha hai chhat se").orElseThrow();
            assertEquals(TriageEngine.Category.PLUMBING, r.category());
            assertEquals(TriageEngine.Priority.MEDIUM, r.priority());
            assertEquals(TriageEngine.Source.LLM, r.source());
            assertEquals("AI: Water is leaking from the ceiling.", r.reason());
        }
    }

    @Test
    void requestCarriesKeyModelAndTheComplaintExactly() throws Exception {
        try (Fake f = new Fake(200, reply("{\"category\":\"NOISE\",\"priority\":\"LOW\",\"reason\":\"x\"}"), 0)) {
            f.client(2000).classify("Loud \"music\" at night\nplease help \\ now");
            assertEquals("Bearer test-key", f.lastAuth.get());
            assertEquals("/openai/v1/chat/completions", f.lastPath.get());
            String body = f.lastBody.get();
            assertTrue(body.contains("\"model\":\"test-model\""));
            assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"));
            assertTrue(body.contains("Loud \\\"music\\\" at night\\nplease help \\\\ now"));
        }
    }

    @Test
    void answerInsideCodeFencesIsStillRead() throws Exception {
        String fenced = "```json\n{\"category\":\"lift\",\"priority\":\"high\",\"reason\":\"Lift broken.\"}\n```";
        try (Fake f = new Fake(200, reply(fenced), 0)) {
            var r = f.client(2000).classify("Lift not working").orElseThrow();
            assertEquals(TriageEngine.Category.LIFT, r.category());
            assertEquals(TriageEngine.Priority.HIGH, r.priority());
        }
    }

    @Test
    void anUnknownCategoryOrPriorityIsRefused() throws Exception {
        try (Fake f = new Fake(200, reply("{\"category\":\"DELETE_EVERYTHING\",\"priority\":\"HIGH\",\"reason\":\"x\"}"), 0)) {
            assertTrue(f.client(2000).classify("anything").isEmpty());
        }
        try (Fake f = new Fake(200, reply("{\"category\":\"LIFT\",\"priority\":\"URGENT\",\"reason\":\"x\"}"), 0)) {
            assertTrue(f.client(2000).classify("anything").isEmpty());
        }
    }

    @Test
    void serverErrorGarbageAndEmptyAnswersAllGiveEmpty() throws Exception {
        try (Fake f = new Fake(500, "{\"error\":\"boom\"}", 0)) {
            assertTrue(f.client(2000).classify("x y z a b c d e f g").isEmpty());
        }
        try (Fake f = new Fake(200, "not json at all", 0)) {
            assertTrue(f.client(2000).classify("x y z a b c d e f g").isEmpty());
        }
        try (Fake f = new Fake(200, "{\"choices\":[]}", 0)) {
            assertTrue(f.client(2000).classify("x y z a b c d e f g").isEmpty());
        }
        try (Fake f = new Fake(401, "{\"error\":\"bad key\"}", 0)) {
            assertTrue(f.client(2000).classify("x y z a b c d e f g").isEmpty());
        }
    }

    @Test
    void aSlowServerTimesOutAndGivesEmpty() throws Exception {
        try (Fake f = new Fake(200, reply("{\"category\":\"LIFT\",\"priority\":\"LOW\",\"reason\":\"x\"}"), 1500)) {
            long start = System.currentTimeMillis();
            assertTrue(f.client(300).classify("Lift is slow").isEmpty());
            assertTrue(System.currentTimeMillis() - start < 1400);
        }
    }

    @Test
    void anUnreachableServerGivesEmpty() {
        var c = new LlmTriageClient("http://127.0.0.1:1/v1", "k", "m", Duration.ofMillis(500));
        assertTrue(c.classify("Lift is slow").isEmpty());
    }

    @Test
    void theReasonIsCleanedAndCapped() {
        String content = "{\"category\":\"OTHER\",\"priority\":\"LOW\",\"reason\":\"line one\\nline two\\u0007 " + "x".repeat(300) + "\"}";
        var r = LlmTriageClient.parse(reply(content)).orElseThrow();
        assertFalse(r.reason().contains("\n"));
        assertFalse(r.reason().contains("\u0007"));
        assertTrue(r.reason().length() <= 204);
    }

    @Test
    void safetyRuleNeverAsksTheAi() throws Exception {
        try (Fake f = new Fake(200, reply("{\"category\":\"OTHER\",\"priority\":\"LOW\",\"reason\":\"fine\"}"), 0)) {
            var service = new TriageService(f.client(2000));
            var r = service.classify("There is a gas leak smell in the corridor");
            assertEquals(TriageEngine.Source.SAFETY_RULE, r.source());
            assertEquals(TriageEngine.Priority.HIGH, r.priority());
            assertEquals(0, f.calls.get());
        }
    }

    @Test
    void aiDecidesWhenThereIsNoSafetyWord() throws Exception {
        try (Fake f = new Fake(200, reply("{\"category\":\"HOUSEKEEPING\",\"priority\":\"MEDIUM\",\"reason\":\"Garbage not collected.\"}"), 0)) {
            var service = new TriageService(f.client(2000));
            var r = service.classify("Kachra do din se uthaya nahi gaya hai");
            assertEquals(TriageEngine.Source.LLM, r.source());
            assertEquals(TriageEngine.Category.HOUSEKEEPING, r.category());
            assertEquals(1, f.calls.get());
        }
    }

    @Test
    void ifTheAiFailsTheKeywordRulesDecide() throws Exception {
        try (Fake f = new Fake(500, "oops", 0)) {
            var service = new TriageService(f.client(2000));
            var r = service.classify("Water leakage from the bathroom ceiling in my flat");
            assertTrue(r.source() != TriageEngine.Source.LLM);
            assertEquals(TriageEngine.Category.PLUMBING, r.category());
        }
    }

    @Test
    void withoutAKeyTheAiIsOffAndRulesDecide() {
        var service = new TriageService("", "http://127.0.0.1:1/v1", "m", 1000);
        assertFalse(service.aiEnabled());
        assertEquals(TriageEngine.Category.LIFT, service.classify("Lift is not working since this morning").category());
    }

    @Test
    void miniJsonReadsNestedValuesAndEscapes() {
        @SuppressWarnings("unchecked")
        var m = (java.util.Map<String, Object>) MiniJson.parse("{\"a\":[1,2.5,true,null,\"x\\u0041\\n\"],\"b\":{\"c\":\"d\"}}");
        assertEquals(java.util.Arrays.asList(1.0, 2.5, true, null, "xA\n").toString(), m.get("a").toString());
    }
}