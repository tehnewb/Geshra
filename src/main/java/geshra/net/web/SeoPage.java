package geshra.net.web;

import geshra.net.web.ui.BrowserJson;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Small fluent description of a public page, delivered in its initial HTTP HTML and updated on
 * client navigation. Configure shared instances at startup. contentHtml is explicitly trusted
 * application HTML; content escapes ordinary text. No crawler-specific user-agent behavior is used.
 */
public final class SeoPage {
    private String title; // Page and social-sharing title.
    private String description; // Search and social-sharing description.
    private String canonical; // Optional explicit canonical URL.
    private String image; // Optional social-sharing image URL.
    private String language = "en"; // Document language.
    private String content = ""; // Trusted initial HTML, or escaped text wrapped by content.
    private String structuredData; // Pre-encoded JSON-LD safe for an HTML script element.
    private boolean indexed = true; // Whether the page is eligible for indexing and the sitemap.
    private Map<String, String> metadata; // Lazily allocated additional named meta tags.

    /**
     * Creates page metadata with a descriptive title.
     * @param title page title
     */
    public SeoPage(String title) { this.title = Objects.requireNonNull(title, "title"); }

    /**
     * Assigns a search snippet and social description.
     * @param text description text
     * @return this page
     */
    public SeoPage description(String text) { description = text; return this; }

    /**
     * Assigns a canonical HTTP(S) URL.
     * @param url absolute public URL
     * @return this page
     */
    public SeoPage canonical(String url) { canonical = httpUrl(url); return this; }

    /**
     * Assigns an absolute social-sharing image URL.
     * @param url HTTP(S) image URL
     * @return this page
     */
    public SeoPage image(String url) { image = httpUrl(url); return this; }

    /**
     * Sets the document language.
     * @param language language tag
     * @return this page
     */
    public SeoPage language(String language) { this.language = Objects.requireNonNull(language, "language"); return this; }

    /**
     * Adds safely escaped initial text for clients without JavaScript.
     * @param text visible initial text
     * @return this page
     */
    public SeoPage content(String text) { content = "<main><h1>" + escape(title) + "</h1><p>" + escape(text) + "</p></main>"; return this; }

    /**
     * Adds trusted application HTML to the initial HTTP body.
     * @param html trusted, non-personalized HTML
     * @return this page
     */
    public SeoPage contentHtml(String html) { content = html == null ? "" : html; return this; }

    /**
     * Adds JSON-LD structured data, escaping script-closing characters.
     * @param data JSON-compatible schema.org object
     * @return this page
     */
    public SeoPage structuredData(Map<String, ?> data) { structuredData = BrowserJson.encode(data)
            .replace("<", "\\u003c"); return this; }

    /**
     * Excludes this page from indexing and generated sitemaps.
     * @return this page
     */
    public SeoPage noIndex() { indexed = false; return this; }

    /**
     * Adds a named metadata tag with escaped content.
     * @param name meta name
     * @param value meta content
     * @return this page
     */
    public SeoPage meta(String name, String value) {
        if (metadata == null) metadata = new LinkedHashMap<>();
        if (metadata.size() >= 64 && !metadata.containsKey(name)) throw new IllegalStateException("Too many metadata tags");
        metadata.put(name, value);
        return this;
    }

    /**
     * Reports whether this page may be indexed.
     * @return index eligibility
     */
    public boolean isIndexed() { return indexed; }

    /**
     * Returns initial trusted or escaped body HTML.
     * @return initial HTML
     */
    public String getContentHtml() { return content; }

    /**
     * Returns the page title.
     * @return title text
     */
    public String getTitle() { return title; }

    /**
     * Returns the document language.
     * @return language tag
     */
    public String getLanguage() { return language; }

    /**
     * Builds escaped head tags shared by HTTP and client navigation.
     * @param defaultCanonical configured public origin and request path, or empty
     * @return safe head HTML
     */
    public String head(String defaultCanonical) {
        StringBuilder html = new StringBuilder(512);
        html.append("<title data-geshra-seo>")
                .append(escape(title))
                .append("</title>");
        tag(html, "name", "description", description);
        tag(html, "name", "robots", indexed ? "index,follow" : "noindex,nofollow");
        tag(html, "property", "og:title", title);
        tag(html, "property", "og:description", description);
        tag(html, "property", "og:type", "website");
        tag(html, "property", "og:image", image);
        tag(html, "name", "twitter:card", image == null ? "summary" : "summary_large_image");
        tag(html, "name", "twitter:title", title);
        tag(html, "name", "twitter:description", description);
        tag(html, "name", "twitter:image", image);
        String url = canonical == null ? defaultCanonical : canonical;
        if (url != null && !url.isEmpty()) {
            html.append("<link data-geshra-seo rel=\"canonical\" href=\"")
                    .append(escape(url))
                    .append("\">");
            tag(html, "property", "og:url", url);
        }
        if (metadata != null) metadata.forEach((name, value) -> tag(html, "name", name, value));
        if (structuredData != null) html.append("<script data-geshra-seo type=\"application/ld+json\">")
                .append(structuredData)
                .append("</script>");
        return html.toString();
    }

    /**
     * Escapes text for HTML and XML text/attribute contexts.
     * @param text source text
     * @return escaped text
     */
    public static String escape(String text) {
        /*
         * One linear pass prevents HTML and XML injection without a regex replacement chain.
         */
        if (text == null) return "";
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            switch (text.charAt(i)) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                case '\'' -> result.append("&#39;");
                default -> result.append(text.charAt(i));
            }
        }
        return result.toString();
    }

    /**
     * Appends one escaped meta tag when a value is available.
     * @param html destination
     * @param attribute name or property
     * @param name metadata name
     * @param value content or null
     */
    private void tag(StringBuilder html, String attribute, String name, String value) {
        if (value != null) html.append("<meta data-geshra-seo ")
                .append(attribute)
                .append("=\"")
                .append(escape(name))
                .append("\" content=\"")
                .append(escape(value))
                .append("\">");
    }

    /**
     * Validates an explicit absolute HTTP(S) URL.
     * @param url supplied URL
     * @return validated URL
     */
    private String httpUrl(String url) {
        URI uri = URI.create(url);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getRawUserInfo() != null) throw new IllegalArgumentException("An absolute HTTP(S) URL is required");
        return url;
    }
}
