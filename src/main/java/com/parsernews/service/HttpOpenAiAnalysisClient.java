package com.parsernews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsernews.persistence.AiReviewConfidence;
import com.parsernews.persistence.AiReviewVerdict;
import com.parsernews.persistence.ManualReviewReason;
import com.parsernews.persistence.ManualReviewStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HttpOpenAiAnalysisClient implements OpenAiAnalysisClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpOpenAiAnalysisClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${openai.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${openai.read-timeout-seconds:45}") long readTimeoutSeconds
    ) {
        // Without explicit timeouts the RestClient can hang indefinitely if OpenAI stalls, which
        // would block the dispatch pipeline (it holds a run guard). A read timeout turns a hung
        // call into a normal "AI review failed" that the caller already handles and skips past.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .withReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = restClientBuilder
                .baseUrl("https://api.openai.com")
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
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
                    outputText,
                    structured.offerPricePerShare(),
                    structured.targetCompany(),
                    structured.acquirerCompany()
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException("OpenAI request failed: " + safeMessage(exception), exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI response could not be parsed: " + safeMessage(exception), exception);
        }
    }

    @Override
    public PriceVerificationResult verifyOfferPrice(
            String model,
            String apiKey,
            String evidence,
            java.math.BigDecimal candidatePrice,
            String targetCompany
    ) {
        try {
            String input = "TARGET (company being acquired): " + (targetCompany == null ? "unknown" : targetCompany)
                    + "\nCANDIDATE per-share offer price to verify: "
                    + (candidatePrice == null ? "none extracted yet" : candidatePrice.toPlainString())
                    + "\n\nEVIDENCE:\n" + evidence;
            JsonNode response = restClient.post()
                    .uri("/v1/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody(model, priceVerifierPrompt(), input, "offer_price_verification", priceVerifierSchema()))
                    .retrieve()
                    .body(JsonNode.class);
            String outputText = outputText(response);
            OpenAiPriceVerification v = objectMapper.readValue(outputText, OpenAiPriceVerification.class);
            return new PriceVerificationResult(
                    v.status(),
                    v.verifiedPrice(),
                    v.currency(),
                    v.basis(),
                    v.quote(),
                    v.note(),
                    outputText
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException("OpenAI price-verify request failed: " + safeMessage(exception), exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI price-verify response could not be parsed: " + safeMessage(exception), exception);
        }
    }

    private String priceVerifierPrompt() {
        return """
                You are a meticulous fact-checker verifying ONE number: the per-share cash offer price
                that the TARGET's shareholders will receive in this M&A deal. You are given a candidate
                price extracted by an earlier pass; independently confirm or correct it from the evidence.

                Rules:
                - Find the exact per-share cash consideration for the company being ACQUIRED (the target),
                  not the acquirer, not a dividend, not EPS/earnings, not a 52-week or historical price.
                - "quote" MUST be the exact sentence (verbatim) from the evidence stating the price. If no
                  such sentence exists, quote must be empty.
                - status: VERIFIED if the candidate equals the price the evidence supports; CORRECTED if a
                  different value is correct (put it in verifiedPrice); NOT_A_CASH_PRICE if the deal is
                  all-stock / undisclosed / not a fixed cash-per-share amount; NO_EVIDENCE if the evidence
                  does not state any offer price.
                - basis: PER_SHARE, PER_ADS, PER_UNIT, or UNKNOWN. Watch for per-ADS vs per-share on
                  foreign targets — that is a common error.
                - verifiedPrice: the correct numeric per-share cash price, or null if none applies.
                - Never guess. If unsure, prefer NO_EVIDENCE with null verifiedPrice.
                Return only the requested structured JSON.
                """;
    }

    private Map<String, Object> priceVerifierSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "status", Map.of("type", "string",
                                "enum", List.of("VERIFIED", "CORRECTED", "NOT_A_CASH_PRICE", "NO_EVIDENCE")),
                        "verifiedPrice", Map.of("type", List.of("number", "null")),
                        "currency", Map.of("type", List.of("string", "null")),
                        "basis", Map.of("type", "string",
                                "enum", List.of("PER_SHARE", "PER_ADS", "PER_UNIT", "UNKNOWN")),
                        "quote", Map.of("type", "string"),
                        "note", Map.of("type", "string")
                ),
                "required", List.of("status", "verifiedPrice", "currency", "basis", "quote", "note")
        );
    }

    private Map<String, Object> requestBody(String model, String prompt, String input) {
        return requestBody(model, prompt, input, "deal_group_ai_review", schema());
    }

    private Map<String, Object> requestBody(String model, String prompt, String input, String schemaName, Map<String, Object> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("input", List.of(
                Map.of("role", "system", "content", prompt),
                Map.of("role", "user", "content", input)
        ));
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", schemaName,
                "strict", true,
                "schema", schema
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
                        "targetCompany", Map.of("type", List.of("string", "null")),
                        "acquirerCompany", Map.of("type", List.of("string", "null")),
                        "offerPricePerShare", Map.of("type", List.of("number", "null")),
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
                        "targetCompany",
                        "acquirerCompany",
                        "offerPricePerShare",
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
            java.math.BigDecimal offerPricePerShare,
            String targetCompany,
            String acquirerCompany,
            List<String> riskFlags
    ) {
    }

    private record OpenAiPriceVerification(
            String status,
            java.math.BigDecimal verifiedPrice,
            String currency,
            String basis,
            String quote,
            String note
    ) {
    }
}
