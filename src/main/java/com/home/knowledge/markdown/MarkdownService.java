package com.home.knowledge.markdown;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Converts Markdown text into sanitized HTML that can be safely embedded in templates.
 */
@Component
public class MarkdownService {

    private static final Safelist SAFE_LIST = new Safelist()
            .addTags("p", "ul", "ol", "li", "strong", "em", "h1", "h2", "h3", "h4",
                    "blockquote", "code", "pre", "hr", "br", "a")
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "http", "https")
            .addEnforcedAttribute("a", "rel", "nofollow noopener");

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().build();
    }

    public String render(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        Node document = parser.parse(markdown);
        String html = renderer.render(document);
        return Jsoup.clean(html, SAFE_LIST);
    }
}
