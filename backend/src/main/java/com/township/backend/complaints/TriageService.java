package com.township.backend.complaints;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TriageService {

    private final LlmTriageClient llm;

    @Autowired
    public TriageService(@Value("${AI_API_KEY:}") String apiKey,
                         @Value("${AI_BASE_URL:https://api.groq.com/openai/v1}") String baseUrl,
                         @Value("${AI_MODEL:llama-3.3-70b-versatile}") String model,
                         @Value("${AI_TIMEOUT_MS:5000}") long timeoutMs) {
        this(apiKey == null || apiKey.isBlank()
                ? null
                : new LlmTriageClient(baseUrl, apiKey.trim(), model, Duration.ofMillis(timeoutMs)));
    }

    TriageService(LlmTriageClient llm) {
        this.llm = llm;
    }

    public boolean aiEnabled() {
        return llm != null;
    }

    public TriageEngine.Result classify(String text) {
        TriageEngine.Result rules = TriageEngine.classify(text);
        if (rules.source() == TriageEngine.Source.SAFETY_RULE || llm == null) {
            return rules;
        }
        return llm.classify(text).orElse(rules);
    }
}