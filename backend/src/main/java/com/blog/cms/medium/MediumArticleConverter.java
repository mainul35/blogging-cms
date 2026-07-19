package com.blog.cms.medium;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

// Pure, dependency-free parsing/conversion of Medium's internal article JSON
// into this blog's markdown. No Spring/reactive/I/O here on purpose, so the
// whole conversion pipeline is testable with a plain JUnit test and a hand
// -built fixture. Medium's exact response shape is undocumented -- this is
// reverse-engineered from the shape used by several well-known open-source
// Medium-to-markdown converters, not verified against a live response.
// Treat paragraph-type coverage as best-effort: an unrecognized type must
// never abort the whole import, only degrade gracefully.
public final class MediumArticleConverter {

    private static final String MIRO_BASE = "https://miro.medium.com/max/1400/";

    private MediumArticleConverter() {
    }

    // Medium's /_/api/* endpoints prefix the real JSON with an XSSI-protection
    // string (historically `])}while(1);</x>`). Stripped by locating the
    // first '{' rather than hardcoding that exact prefix, since it's an
    // undocumented internal API detail that could vary between endpoints or
    // change over time.
    public static String stripXssiPrefix(String raw) {
        int idx = raw.indexOf('{');
        if (idx < 0) {
            throw new IllegalArgumentException("No JSON object found in Medium response");
        }
        return raw.substring(idx);
    }

    public static JsonNode extractPayloadValue(JsonNode root) {
        JsonNode value = root.path("payload").path("value");
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("Could not locate payload.value in Medium response -- "
                    + "check you copied the article-data request from DevTools, not a different one");
        }
        return value;
    }

    public record ParagraphBlock(String type, String renderedText, String imageId, String embedHref) {
    }

    public static String imageCdnUrl(String mediumImageId) {
        return MIRO_BASE + mediumImageId;
    }

    // Exact-host-or-subdomain check, not a substring/contains check -- rejects
    // suffix-spoofing hosts like "medium.com.evil.com". Pulled out as a pure,
    // static, dependency-free method (rather than living inline in
    // MediumImportService) specifically so it's table-testable without a
    // Spring context.
    public static boolean isAllowedFetchHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return h.equals("medium.com") || h.endsWith(".medium.com");
    }

    // Unrecognized types fall through to a plain-text passthrough (with a
    // warning appended to `warnings`) rather than being dropped or throwing --
    // losing a paragraph silently would be worse than rendering it as an
    // unstyled line.
    public static List<ParagraphBlock> parseParagraphs(JsonNode paragraphsNode, List<String> warnings) {
        List<ParagraphBlock> blocks = new ArrayList<>();
        if (!paragraphsNode.isArray()) {
            return blocks;
        }
        for (JsonNode node : paragraphsNode) {
            String type = node.path("type").asText("");
            String text = node.path("text").asText("");
            switch (type) {
                case "IMG" -> {
                    String imageId = node.path("metadata").path("id").asText(null);
                    if (imageId != null && !imageId.isBlank()) {
                        blocks.add(new ParagraphBlock(type, null, imageId, null));
                    }
                }
                case "IFRAME", "MIXTAPE_EMBED" -> {
                    String href = node.path("iframe").path("mediaResource").path("href").asText(null);
                    if (href == null || href.isBlank()) {
                        href = node.path("metadata").path("href").asText(null);
                    }
                    if (href != null && !href.isBlank()) {
                        blocks.add(new ParagraphBlock(type, null, null, href));
                    }
                }
                case "H3", "H4", "P", "BQ", "PQ", "PRE", "CODE_BLOCK", "ULI", "OLI" ->
                        blocks.add(new ParagraphBlock(type, applyMarkups(text, node.path("markups")), null, null));
                default -> {
                    if (!text.isBlank()) {
                        warnings.add("Skipped unrecognized paragraph type: " + type);
                        blocks.add(new ParagraphBlock(type, applyMarkups(text, node.path("markups")), null, null));
                    }
                }
            }
        }
        return blocks;
    }

    // Inserts markdown syntax at each markup's start/end character offset.
    // Markups are processed sorted by `start` DESCENDING so that inserting
    // characters for a later markup never shifts the offsets a not-yet
    // -processed earlier markup depends on -- the standard technique for
    // offset-based markup application. Correct for Medium's typical flat,
    // non-overlapping spans; deeply nested/overlapping markups on the same
    // text are an accepted imprecision for this best-effort v1.
    public static String applyMarkups(String text, JsonNode markupsNode) {
        if (text == null) return "";
        if (!markupsNode.isArray() || markupsNode.isEmpty()) return text;

        record Markup(int start, int end, String type, String href) {
        }
        List<Markup> markups = new ArrayList<>();
        for (JsonNode m : markupsNode) {
            int start = m.path("start").asInt(-1);
            int end = m.path("end").asInt(-1);
            if (start < 0 || end < 0 || end > text.length() || start >= end) continue;
            markups.add(new Markup(start, end, m.path("type").asText(""), m.path("href").asText("")));
        }
        markups.sort((a, b) -> Integer.compare(b.start(), a.start()));

        StringBuilder sb = new StringBuilder(text);
        for (Markup m : markups) {
            String prefix;
            String suffix;
            switch (m.type()) {
                case "STRONG" -> {
                    prefix = "**";
                    suffix = "**";
                }
                case "EM" -> {
                    prefix = "*";
                    suffix = "*";
                }
                case "CODE" -> {
                    prefix = "`";
                    suffix = "`";
                }
                case "A" -> {
                    prefix = "[";
                    suffix = "](" + m.href() + ")";
                }
                default -> {
                    prefix = "";
                    suffix = "";
                }
            }
            if (prefix.isEmpty() && suffix.isEmpty()) continue;
            sb.insert(m.end(), suffix);
            sb.insert(m.start(), prefix);
        }
        return sb.toString();
    }

    // Markdown for every block type except IMG, which needs an async image
    // download the caller must resolve first -- returns null for IMG so a
    // caller that forgets to special-case it fails loudly instead of writing
    // a broken image line.
    public static String markdownFor(ParagraphBlock block) {
        return switch (block.type()) {
            case "H3" -> "### " + block.renderedText();
            case "H4" -> "#### " + block.renderedText();
            case "BQ", "PQ" -> "> " + block.renderedText();
            case "PRE", "CODE_BLOCK" -> "```\n" + block.renderedText() + "\n```";
            case "ULI" -> "- " + block.renderedText();
            case "OLI" -> "1. " + block.renderedText();
            case "IFRAME", "MIXTAPE_EMBED" -> block.embedHref() != null
                    ? "[Embedded content](" + block.embedHref() + ")" : null;
            case "IMG" -> null;
            default -> block.renderedText();
        };
    }

    private static boolean isListType(String type) {
        return "ULI".equals(type) || "OLI".equals(type);
    }

    // Joins already-rendered per-block markdown (image lines already
    // substituted by the caller) into the final document. Consecutive list
    // items of the same kind join with a single newline so they render as one
    // list; everything else joins with a blank line.
    public static String assembleMarkdown(List<String> blockMarkdown, List<String> blockTypes) {
        StringBuilder sb = new StringBuilder();
        String prevType = null;
        for (int i = 0; i < blockMarkdown.size(); i++) {
            String md = blockMarkdown.get(i);
            if (md == null || md.isBlank()) continue;
            String type = blockTypes.get(i);
            if (sb.length() > 0) {
                boolean sameList = isListType(type) && type.equals(prevType);
                sb.append(sameList ? "\n" : "\n\n");
            }
            sb.append(md);
            prevType = type;
        }
        return sb.toString();
    }
}
