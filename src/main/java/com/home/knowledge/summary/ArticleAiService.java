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
    private static final Pattern FIGURE_PATTERN = Pattern.compile("(図|Fig(?:\\.|ure)?)\\s*[0-9０-９]+", Pattern.CASE_INSENSITIVE);

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
        String normalizedText = normalizeBody(text);
        String title = extractTitle(url);
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(normalizedText)) {
            String structured = buildStructuredMarkdown(normalizedText);
            return ArticleDraft.of(title, structured, createSummaryFallback(normalizedText));
        }
        String prompt = createPrompt(normalizedText);
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
                String aiContent = ensureReadableContent(parsed.path("content").asText(), normalizedText);
                String aiSummary = ensureSummaryText(parsed.path("summary").asText(), aiContent);
                return ArticleDraft.of(aiTitle, aiContent, aiSummary);
            }
        } catch (IOException e) {
            log.warn("Failed to summarize article {} because of IO error", url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Summarization interrupted for {}", url, e);
        }
        return ArticleDraft.of(title, buildStructuredMarkdown(normalizedText), createSummaryFallback(normalizedText));
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
        return """
以下のニュース記事を読み、日本語のJSON文字列で次のキーを必ず含めて返してください。
- title: 40字以内の見出し。
- content: Markdown形式で3〜4個の見出し(##)を用い、各見出しの本文は2〜3文で要点のみを落ち着いた文体でまとめる。記事に含まれていない情報や推測は書かない。
- summary: 3〜5行（改行区切り）の紹介文。静かなトーンで読者の関心を喚起し、どんな人が読むと良いかを示す。
- 図や画像への参照語（例: 図1、Fig.2）は使わず、文章だけで説明する。

記事本文:
""" + summaryText;
    }

    private String createSummaryFallback(String text) {
        return craftCalmSummary(text);
    }

    private String ensureReadableContent(String candidate, String fallbackSource) {
        String normalized = normalizeBody(candidate);
        if (!StringUtils.hasText(normalized)) {
            return buildStructuredMarkdown(fallbackSource);
        }
        String concise = shortenContent(normalized);
        if (containsMarkdownHints(concise)) {
            return concise;
        }
        return buildStructuredMarkdown(concise);
    }

    private String ensureSummaryText(String candidate, String contentSource) {
        String normalized = normalizeBody(candidate);
        if (!StringUtils.hasText(normalized)) {
            return craftCalmSummary(contentSource);
        }
        if (normalized.contains("紹介:") && normalized.contains("対象:")) {
            return normalized;
        }
        return craftCalmSummary(normalized);
    }

    private boolean containsMarkdownHints(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("## ") || text.contains("**") || text.contains("\n- ") || text.contains("\n* ");
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
            if (!StringUtils.hasText(trimmed)) continue;
            chosen.add(trimmed);
            if (chosen.size() >= 4) {
                break;
            }
        }
        if (chosen.size() < 3) {
            chosen.clear();
            String singleLine = normalized.replaceAll("\\s+", " ").trim();
            int avgLength = Math.max(40, singleLine.length() / 3);
            for (int i = 0; i < singleLine.length() && chosen.size() < 4; i += avgLength) {
                String chunk = singleLine.substring(i, Math.min(singleLine.length(), i + avgLength)).trim();
                if (StringUtils.hasText(chunk)) {
                    chosen.add(ensureSentenceClosed(chunk));
                }
            }
        }
        while (chosen.size() < 3 && !chosen.isEmpty()) {
            chosen.add(chosen.get(chosen.size() - 1));
        }
        if (chosen.isEmpty()) {
            chosen.add("紹介: 記事の概要を静かに振り返ります。");
        } else {
            if (!chosen.get(0).startsWith("紹介:")) {
                chosen.set(0, "紹介: " + chosen.get(0));
            }
        }
        String calmLine = "対象: 静かなトーンで背景を理解したい方におすすめです。";
        if (chosen.size() >= 5) {
            chosen.set(chosen.size() - 1, calmLine);
        } else {
            chosen.add(calmLine);
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
        if (text.endsWith("。") || text.endsWith("!") || text.endsWith("！") || text.endsWith("?") || text.endsWith("？")) {
            return text;
        }
        return text + "。";
    }

    private String shortenContent(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (containsMarkdownHints(text)) {
            return trimMarkdownParagraphs(text);
        }
        List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) {
            return text;
        }
        int limit = Math.min(10, sentences.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append(sentences.get(i));
            if (i != limit - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String trimMarkdownParagraphs(String markdown) {
        String[] blocks = markdown.split("\\n\\s*\\n");
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (String block : blocks) {
            if (!StringUtils.hasText(block)) {
                continue;
            }
            sb.append(block.trim()).append("\n\n");
            kept++;
            if (kept >= 6) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private String buildStructuredMarkdown(String text) {
        String normalized = normalizeBody(text);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        List<String> sentences = splitIntoSentences(normalized);
        if (sentences.isEmpty()) {
            return normalized;
        }
        StringBuilder md = new StringBuilder();
        md.append("## 要約\n\n").append(sentences.get(0)).append("\n\n");
        if (sentences.size() > 1) {
            md.append("## 注目ポイント\n\n");
            sentences.stream()
                    .skip(1)
                    .limit(4)
                    .forEach(s -> md.append("- " ).append(s).append("\n"));
            md.append("\n");
        }
        md.append("## 詳細\n\n");
        for (int i = 0; i < sentences.size(); i++) {
            md.append(sentences.get(i));
            if ((i + 1) % 3 == 0 || i == sentences.size() - 1) {
                md.append("\n\n");
            } else {
                md.append(" " );
            }
        }
        return md.toString().trim();
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
    public record ArticleDraft(String title, String content, String summary) {
        public static ArticleDraft of(String title, String content, String summary) {
            return new ArticleDraft(title != null ? title : "", content != null ? content : "",
                    summary != null ? summary : "");
        }
    }
}
