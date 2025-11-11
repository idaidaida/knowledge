package com.home.knowledge.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ArticleAiService {
    private static final Logger log = LoggerFactory.getLogger(ArticleAiService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; KnowledgeFetcher/1.0)";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ArticleAiService(@Value("${openai.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ArticleDraft buildDraft(String url) {
        String text = fetchArticleText(url);
        String title = extractTitle(url);
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(text)) {
            return ArticleDraft.of(title, text, createSummaryFallback(text));
        }
        String prompt = createPrompt(text);
        Map<String, Object> payload = Map.of(
                "model", "gpt-3.5-turbo",
                "temperature", 0.3,
                "max_tokens", 400,
                "messages", List.of(
                        Map.of("role", "system", "content", "あなたは日本語のニュースを要約し、タイトルと本文を整える編集者です。"),
                        Map.of("role", "user", "content", prompt)));
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OpenAI summarize call returned {}: {}", response.statusCode(), response.body());
                return ArticleDraft.of(title, text, createSummaryFallback(text));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual()) {
                String trimmed = content.asText().trim();
                JsonNode parsed = objectMapper.readTree(trimmed);
                String aiTitle = parsed.path("title").asText(title);
                String aiContent = parsed.path("content").asText(text);
                String aiSummary = parsed.path("summary").asText(createSummaryFallback(text));
                return ArticleDraft.of(aiTitle, aiContent, aiSummary);
            }
        } catch (IOException e) {
            log.warn("Failed to summarize article {} because of IO error", url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Summarization interrupted for {}", url, e);
        }
        return ArticleDraft.of(title, text, createSummaryFallback(text));
    }

    private String extractTitle(String url) {
        try {
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(8000).get();
            String t = doc.title();
            return StringUtils.hasText(t) ? t : "タイトルなし";
        } catch (IOException e) {
            log.debug("Unable to fetch title at {}", url);
            return "タイトルなし";
        }
    }

    private String fetchArticleText(String url) {
        try {
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(8000).get();
            String article = doc.select("article").text();
            if (!StringUtils.hasText(article)) {
                article = doc.body() != null ? doc.body().text() : "";
            }
            return article;
        } catch (IOException e) {
            log.debug("Unable to fetch article at {}: {}", url, e.getMessage());
            return "";
        }
    }

    private String createPrompt(String text) {
        String summaryText = text.length() > 2000 ? text.substring(0, 2000) : text;
        return "このニュース記事から、JSON形式で次のキーを返してください。"
                + " title（日本語の見出し/40文字以内）、"
                + " content（新聞記者のようにわかりやすく端的で、かつ詳細な記事の内容）、"
                + " summary（新聞記者のようにわかりやすく端的で、かつ詳細な記事の内容）。"
                + " 記事は次の内容です：\n" + summaryText;
    }

    private String createSummaryFallback(String text) {
        if (!StringUtils.hasText(text))
            return "";
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    public record ArticleDraft(String title, String content, String summary) {
        public static ArticleDraft of(String title, String content, String summary) {
            return new ArticleDraft(title != null ? title : "", content != null ? content : "",
                    summary != null ? summary : "");
        }
    }
}
