package com.mcp.tools.tool;

import com.mcp.tools.annotation.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FetchWebpageTool {

    private final WebClient webClient;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_CONTENT_LENGTH = 3000;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[^\\s，。；！？、\\)）】」〉》\"'\\`<>]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    public FetchWebpageTool() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @McpTool(
            name = "fetch_webpage",
            description = "抓取指定网页的文本内容并提取正文。参数 url：网页链接",
            tags = {"fetch", "web", "scrape"}
    )
    public String fetchWebpage(String url) {
        if (url == null || url.isBlank()) {
            return "错误：未提供有效的网页链接。";
        }

        String normalizedUrl = url.trim()
                .replaceAll("^[`'\"\\s]+|[`'\"\\s]+$", "")
                .replace(" ", "");
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            normalizedUrl = "https://" + normalizedUrl;
        }

        log.info("Fetching webpage: {}", normalizedUrl);

        try {
            String html = webClient.get()
                    .uri(normalizedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block(REQUEST_TIMEOUT);

            if (html == null || html.isBlank()) {
                String errMsg = html == null ? "响应为空（null）" : "响应内容为空";
                log.warn("Webpage returned empty: {} - {}", normalizedUrl, errMsg);
                return "错误：" + errMsg + "，网页可能拒绝访问或触发了反爬机制。\n"
                        + "链接: " + normalizedUrl + "\n"
                        + "提示：请稍等片刻后重试，或手动访问该链接。";
            }

            String text = extractText(html);

            if (text.isBlank()) {
                return "提示：网页内容为空或无法提取有效文本，可能是纯 JavaScript 渲染页面。\n"
                        + "建议：手动访问该链接并复制文本内容。\n链接: " + normalizedUrl;
            }

            String title = extractTitle(html);
            String summary = text.length() > MAX_CONTENT_LENGTH
                    ? text.substring(0, MAX_CONTENT_LENGTH)
                    + "\n\n... (内容过长，已截断，共 " + text.length() + " 字符)"
                    : text;

            log.info("Webpage fetched: {} ({} chars)", normalizedUrl, text.length());

            return "网页标题: " + title + "\n"
                    + "链接: " + normalizedUrl + "\n"
                    + "原文长度: " + text.length() + " 字符\n"
                    + "---\n" + summary;
        } catch (Exception e) {
            String cause = e.getMessage();
            if (cause != null && cause.contains("Timeout")) {
                log.warn("Webpage fetch timeout: {} - {}", normalizedUrl, cause);
                return "错误：请求超时（10秒），网页响应过慢或网络不通。\n链接: " + normalizedUrl;
            }
            log.error("Failed to fetch webpage: {} - {}", normalizedUrl, cause);
            return "错误：抓取网页失败。\n链接: " + normalizedUrl + "\n原因: " + cause;
        }
    }

    private String extractTitle(String html) {
        Matcher m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            return m.group(1).replaceAll("\\s+", " ").trim();
        }
        return "（未提取到标题）";
    }

    private String extractText(String html) {
        String text = html
                .replaceAll("(?si)<head[^>]*>.*?</head>", " ")
                .replaceAll("(?si)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?si)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?si)<noscript[^>]*>.*?</noscript>", " ")
                .replaceAll("(?si)<svg[^>]*>.*?</svg>", " ")
                .replaceAll("(?si)<iframe[^>]*>.*?</iframe>", " ")
                .replaceAll("(?si)<!--.*?-->", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#?[a-z0-9]+;", " ")
                .replaceAll("[\\t\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n");

        StringBuilder result = new StringBuilder();
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 2) {
                result.append(trimmed).append("\n");
            }
        }
        return result.toString().trim();
    }

    public static boolean containsUrl(String text) {
        return text != null && URL_PATTERN.matcher(text).find();
    }

    public static String extractFirstUrl(String text) {
        if (text == null) return null;
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) {
            String url = m.group(1);
            url = url.replaceAll("^[`'\"]+|[`'\"]+$", "");
            return url;
        }
        return null;
    }
}