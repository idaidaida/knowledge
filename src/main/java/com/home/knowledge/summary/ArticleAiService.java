package com.home.knowledge.summary;

import com.fasterxml.jackson.core.json.JsonReadFeature;
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
import java.text.BreakIterator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ArticleAiService {
    private static final Logger log = LoggerFactory.getLogger(ArticleAiService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; KnowledgeFetcher/1.0)";
    private static final Pattern FIGURE_PATTERN = Pattern.compile("(図|Fig(?:\\.|ure)?)\\s*[0-9０-９]+",
            Pattern.CASE_INSENSITIVE);
    private static final int PREVIEW_LIMIT = 120;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ArticleAiService(@Value("${openai.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ArticleDraft buildDraft(String url) {
        String text = fetchArticleText(url);
        String normalizedText = normalizeBody(text);
        String title = extractTitle(url);
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(normalizedText)) {
            String structured = buildStructuredMarkdown(normalizedText);
            String fallbackSummary = createSummaryFallback(normalizedText);
            ArticleDraft draft = ArticleDraft.of(title, structured, fallbackSummary);
            debugDraft("fallback:no-api-or-text", draft.title(), draft.summary(), draft.content());
            return draft;
        }
        String prompt = createPrompt(url, normalizedText);
        Map<String, Object> payload = Map.of(
                "model", "gpt-4o-mini",
                "temperature", 0.3,
                "max_tokens", 2000,
                "messages", List.of(
                        Map.of("role", "system", "content", "あなたは難聴の子供の子育てをしている親向けに有益なネットの情報をまとめている記者です。"),
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
                String fallbackSummary = createSummaryFallback(text);
                ArticleDraft draft = ArticleDraft.of(title, text, fallbackSummary);
                debugDraft("fallback:api-error", draft.title(), draft.summary(), draft.content());
                return draft;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual()) {
                String raw = content.asText();
                if (log.isDebugEnabled()) {
                    log.debug("AI raw response: {}", raw);
                }
                JsonNode parsed = parseAiJson(raw);
                String aiTitle = parsed.path("title").asText("");
                String aiContent = parsed.path("content").asText("");
                String aiSummary = parsed.path("summary").asText("");
                ArticleDraft draft = ArticleDraft.of(aiTitle, aiContent, aiSummary);
                debugDraft("ai:success", draft.title(), draft.summary(), draft.content());
                return draft;
            }
        } catch (IOException e) {
            log.warn("Failed to summarize article {} because of IO error", url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Summarization interrupted for {}", url, e);
        }
        String structured = buildStructuredMarkdown(normalizedText);
        String fallbackSummary = createSummaryFallback(normalizedText);
        ArticleDraft draft = ArticleDraft.of(title, structured, fallbackSummary);
        debugDraft("fallback:unexpected-error", draft.title(), draft.summary(), draft.content());
        return draft;
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

    private String createPrompt(String url, String text) {
        String summaryText = text.length() > 2000 ? text.substring(0, 2000) : text;
        StringBuilder prompt = new StringBuilder("""
                # 指示
                以下の記事を読み、以下の情報をまとめてください。
                - 記事のTitle
                - 記事の紹介文
                - 記事の詳細

                # 指示詳細
                記事のTitleは、記事の内容に基づいて端的な表現をあなたが考えてください。
                記事の紹介文は、その記事が子育てをしている親にとってどのように有益かを300文字以内で簡潔に説明してください。
                記事の詳細は、その記事の内容をわかりやすくまとめてください。ある程度詳細にまとめてほしいです。長文になる場合は、章立てや箇条書きなど見やすくなるように工夫してください。

                # アウトプット
                以下のキーを含むJSONでTitleと紹介文と詳細を返してください。
                - title: 記事のtitle
                - summary: 記事の紹介文（300文字以内）
                - content: 記事の詳細。contentはMarkdownの記法で書いてください。長すぎる場合は要約して。
                """);
        prompt.append("\n\n# 記事本文\n").append(summaryText);
        return prompt.toString();
    }

    private String createSummaryFallback(String text) {
        return craftCalmSummary(text);
    }

    private String craftCalmSummary(String source) {
        String normalized = normalizeBody(source);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        List<String> sentences = splitIntoSentences(normalized);
        List<String> chosen = new ArrayList<>();
        for (String sentence : sentences) {
            String trimmed = ensureSentenceClosed(sentence.trim());
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            chosen.add(trimmed);
            if (chosen.size() >= 4) {
                break;
            }
        }
        if (chosen.isEmpty()) {
            chosen.add(ensureSentenceClosed(normalized.replaceAll("\\s+", " ").trim()));
        }
        if (chosen.size() > 5) {
            chosen = chosen.subList(0, 5);
        }
        return String.join("\n", chosen);
    }

    private String ensureSentenceClosed(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.endsWith("。") || text.endsWith("!") || text.endsWith("！") || text.endsWith("?")
                || text.endsWith("？")) {
            return text;
        }
        return text + "。";
    }

    private String buildStructuredMarkdown(String text) {
        return normalizeBody(text);
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return sentences;
        }
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.JAPANESE);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (StringUtils.hasText(sentence)) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private String normalizeBody(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n");
        normalized = FIGURE_PATTERN.matcher(normalized).replaceAll("この図");
        normalized = normalized.replaceAll("[ \t]{2,}", " ");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.trim();
    }

    private void debugDraft(String stage, String title, String summary, String content) {

        log.info("Draft stage [{}] -> title='{}' (len={}), summaryPreview='{}' (len={}), contentPreview='{}' (len={})",
                stage,
                preview(title), title != null ? title.length() : 0,
                preview(summary), summary != null ? summary.length() : 0,
                preview(content), content != null ? content.length() : 0);
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace("\r\n", " ").replace("\n", " ").trim();
        if (compact.length() > PREVIEW_LIMIT) {
            return compact.substring(0, PREVIEW_LIMIT) + "...";
        }
        return compact;
    }

    private JsonNode parseAiJson(String raw) throws IOException {
        String cleaned = stripCodeFence(raw);
        try {
            return objectMapper.readTree(cleaned);
        } catch (IOException primary) {
            int firstBrace = cleaned.indexOf('{');
            int lastBrace = cleaned.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                String candidate = cleaned.substring(firstBrace, lastBrace + 1);
                return objectMapper.readTree(candidate);
            }
            throw primary;
        }
    }

    private String stripCodeFence(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            if (newline > 0) {
                trimmed = trimmed.substring(newline + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public record ArticleDraft(String title, String content, String summary) {
        public static ArticleDraft of(String title, String content, String summary) {
            return new ArticleDraft(title != null ? title : "", content != null ? content : "",
                    summary != null ? summary : "");
        }
    }
}
