package com.parsernews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HttpOpenAiAnalysisClient implements OpenAiAnalysisClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpOpenAiAnalysisClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl("https://api.openai.com").build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AiReviewResult reviewDealGroup(String model, String apiKey, String prompt, String input) {
        try {
            JsonNode response = restClient.post()
                    .uri("/v1/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody(model, prompt, input))
                    .retrieve()
                    .body(JsonNode.class);
            String outputText = outputText(response);
            OpenAiStructuredReview structured = objectMapper.readValue(outputText, OpenAiStructuredReview.class);
            return new AiReviewResult(
                    structured.verdict(),
                    structured.confidence(),
                    structured.tradablePublicTarget(),
                    structured.suggestedReviewStatus(),
                    structured.suggestedReviewReason(),
                    structured.reason(),
                    structured.riskFlags() == null ? List.of() : structured.riskFlags(),
                    outputText
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException("OpenAI request failed: " + safeMessage(exception), exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI response could not be parsed: " + safeMessage(exception), exception);
        }
    }

    private Map<String, Object> requestBody(String model, String prompt, String input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("input", List.of(
                Map.of("role", "system", "content", prompt),
                Map.of("role", "user", "content", input)
        ));
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", "deal_group_ai_review",
                "strict", true,
                "schema", schema()
        )));
        return body;
    }

    private Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "verdict", Map.of("type", "string", "enum", enumNames(AiReviewVerdict.values())),
                        "confidence", Map.of("type", "string", "enum", enumNames(AiReviewConfidence.values())),
                        "tradablePublicTarget", Map.of("type", "boolean"),
                        "suggestedReviewStatus", Map.of("type", "string", "enum", enumNames(ManualReviewStatus.values())),
                        "suggestedReviewReason", Map.of("type", "string", "enum", enumNames(ManualReviewReason.values())),
                        "reason", Map.of("type", "string"),
                        "riskFlags", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )
                ),
                "required", List.of(
                        "verdict",
                        "confidence",
                        "tradablePublicTarget",
                        "suggestedReviewStatus",
                        "suggestedReviewReason",
                        "reason",
                        "riskFlags"
                )
        );
    }

    private List<String> enumNames(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }

    private String outputText(JsonNode response) {
        JsonNode outputText = response.path("output_text");
        if (outputText.isTextual()) {
            return outputText.asText();
        }
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                JsonNode text = content.path("text");
                if (text.isTextual()) {
                    return text.asText();
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not contain output text.");
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private record OpenAiStructuredReview(
            AiReviewVerdict verdict,
            AiReviewConfidence confidence,
            boolean tradablePublicTarget,
            ManualReviewStatus suggestedReviewStatus,
            ManualReviewReason suggestedReviewReason,
            String reason,
            List<String> riskFlags
    ) {
    }
}
