package com.blog.cms.medium;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Pure, dependency-free parsing/conversion of Medium's article data into this
// blog's markdown. No Spring/reactive/I/O here on purpose, so the whole
// conversion pipeline is testable with a plain JUnit test and a hand-built
// fixture.
//
// The "fetch URL" an admin captures from DevTools turns out to be the plain
// article page itself (an HTML document request, not a JSON API call) --
// confirmed against a real article. Medium's Next.js/Apollo frontend embeds
// the full article as a normalized Apollo Client cache in a
// `<script>window.__APOLLO_STATE__ = {...}</script>` block: a flat map of
// "TypeName:id" -> object, where a Post's body paragraphs are `{"__ref":
// "Paragraph:<id>"}` pointers that must be resolved against that same flat
// map rather than inline objects. Once resolved, individual paragraph fields
// (type/text/markups/metadata) match the shape used by well-known
// open-source Medium-to-markdown converters. Treat paragraph-type coverage
// as best-effort: an unrecognized type must never abort the whole import,
// only degrade gracefully.
public final class MediumArticleConverter {

    private static final String MIRO_BASE = "https://miro.medium.com/max/1400/";
    private static final Pattern POST_ID_PATTERN = Pattern.compile("-([0-9a-f]{6,})/?$");
    // Matches Chrome/Edge DevTools' "Copy as fetch" output, e.g.
    // fetch("https://example.com", {"headers": {...}, ...}); -- an admin
    // pasting that whole snippet (a very natural thing to do, since "Copy as
    // fetch" is what shows up right next to "Copy URL" in the Network tab's
    // context menu) would otherwise just get a generic "invalid URL" error.
    private static final Pattern FETCH_SNIPPET_PATTERN =
            Pattern.compile("^\\s*fetch\\(\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private MediumArticleConverter() {
    }

    // Accepts either a bare URL or a full `fetch("URL", {...})` snippet
    // copied from DevTools and returns just the URL either way.
    public static String normalizeFetchUrl(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        Matcher m = FETCH_SNIPPET_PATTERN.matcher(trimmed);
        return m.find() ? m.group(1) : trimmed;
    }

    // Locates the `window.__APOLLO_STATE__ = {...};` assignment in the page
    // HTML and returns the raw JSON object text. Medium unicode-escapes
    // forward slashes inside string values (/ instead of a literal /)
    // specifically to avoid a literal "</script>" ever appearing inside the
    // JSON payload, which makes searching for the first "</script>" after the
    // opening brace a safe way to find the end of the block.
    public static String extractApolloStateJson(String html) {
        int varIdx = html.indexOf("window.__APOLLO_STATE__");
        if (varIdx < 0) {
            throw new IllegalArgumentException("Could not find article data in this page -- "
                    + "check you copied your own article's URL, not some other Medium page");
        }
        int braceIdx = html.indexOf('{', varIdx);
        int scriptEndIdx = html.indexOf("</script>", braceIdx);
        if (braceIdx < 0 || scriptEndIdx < 0) {
            throw new IllegalArgumentException("Could not parse article data block in this page");
        }
        String raw = html.substring(braceIdx, scriptEndIdx).trim();
        if (raw.endsWith(";")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    // Medium's post ID is the trailing hex segment of the article URL's slug
    // (e.g. ".../my-title-d6aa51936735" -> "d6aa51936735"), which is exactly
    // the id half of the Apollo cache's "Post:<id>" key -- no separate
    // lookup/search through the cache is needed to find the right post.
    public static String extractPostIdFromUrl(String url) {
        Matcher m = POST_ID_PATTERN.matcher(url);
        if (!m.find()) {
            throw new IllegalArgumentException("Could not find a Medium post ID in that URL -- "
                    + "paste your article's own URL (the one in your browser's address bar)");
        }
        return m.group(1);
    }

    public static JsonNode findPost(JsonNode apolloState, String postId) {
        JsonNode post = apolloState.path("Post:" + postId);
        if (post.isMissingNode() || post.isNull()) {
            throw new IllegalArgumentException("Could not find this article's data -- "
                    + "the page may not be a Medium story, or Medium's page structure may have changed");
        }
        return post;
    }

    // The GraphQL field holding the article body is keyed by its own
    // serialized arguments (e.g. `content({"postMeteringOptions":{...}})`),
    // which can vary -- matched by prefix rather than the exact argument
    // string so small argument differences don't break the lookup.
    public static JsonNode findContent(JsonNode post) {
        Iterator<String> fieldNames = post.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            if (name.startsWith("content(")) {
                return post.path(name);
            }
        }
        return post.path("content");
    }

    public record ParagraphBlock(String type, String renderedText, String imageId, String embedHref,
                                  String codeLang) {
    }

    public static String imageCdnUrl(String mediumImageId) {
        return MIRO_BASE + mediumImageId;
    }

    // Exact-host-or-subdomain check, not a substring/contains check -- rejects
    // suffix-spoofing hosts like "medium.com.evil.com". Also allows
    // "<username>.medium.com" custom subdomains, which is exactly how a real
    // article URL looked when this was verified. Pulled out as a pure,
    // static, dependency-free method (rather than living inline in
    // MediumImportService) specifically so it's table-testable without a
    // Spring context.
    public static boolean isAllowedFetchHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return h.equals("medium.com") || h.endsWith(".medium.com");
    }

    // `paragraphs` is a JSON array of {"__ref": "Paragraph:<id>"} pointers --
    // resolves each against the flat Apollo cache. An unresolvable ref is
    // skipped with a warning rather than failing the whole import (Medium's
    // cache can, in principle, omit an entity the page didn't end up
    // rendering).
    public static List<JsonNode> resolveParagraphRefs(JsonNode paragraphRefs, JsonNode apolloState,
                                                        List<String> warnings) {
        List<JsonNode> resolved = new ArrayList<>();
        if (!paragraphRefs.isArray()) {
            return resolved;
        }
        for (JsonNode refNode : paragraphRefs) {
            String ref = refNode.path("__ref").asText(null);
            if (ref == null) continue;
            JsonNode paragraph = apolloState.path(ref);
            if (paragraph.isMissingNode() || paragraph.isNull()) {
                warnings.add("Could not resolve paragraph reference: " + ref);
                continue;
            }
            resolved.add(paragraph);
        }
        return resolved;
    }

    // Medium always repeats the article title as the body's own first
    // paragraph (a heading matching the title verbatim) -- confirmed against
    // a real article. That's already rendered separately by the post page
    // template (its own <h1>/title), so leaving it in the markdown body too
    // would show it twice. Only removes an exact match -- a heading that
    // merely resembles the title is left alone.
    //
    // Does NOT also strip the body's copy of the cover/preview image
    // (previous behavior, removed): previewImage.id is typically the same
    // asset as the first inline image, but it's Medium's own pick, not
    // necessarily where the admin wants their post's cover to stay pinned.
    // Silently deleting that paragraph from the body meant that if the admin
    // later changed the cover image in the editor, the original image was
    // gone from the article entirely -- not shown as cover (now something
    // else) and not shown inline (already deleted) -- leaving a gap with no
    // way back except re-importing. Leaving it in the body risks it
    // appearing twice right after import (once as the cover banner, once
    // inline at its original position) -- a purely cosmetic duplicate the
    // admin can delete themselves from the one place they don't want it,
    // which is a much smaller problem than the content loss above.
    public static List<ParagraphBlock> stripDuplicateTitleHeading(List<ParagraphBlock> blocks, String title) {
        List<ParagraphBlock> result = new ArrayList<>(blocks);
        if (!result.isEmpty()) {
            ParagraphBlock first = result.get(0);
            boolean isHeading = "H3".equals(first.type()) || "H4".equals(first.type());
            if (isHeading && title != null && first.renderedText() != null
                    && first.renderedText().trim().equalsIgnoreCase(title.trim())) {
                result.remove(0);
            }
        }
        return result;
    }

    // Unrecognized types fall through to a plain-text passthrough (with a
    // warning appended to `warnings`) rather than being dropped or throwing --
    // losing a paragraph silently would be worse than rendering it as an
    // unstyled line.
    public static List<ParagraphBlock> parseParagraphs(List<JsonNode> paragraphNodes, List<String> warnings) {
        List<ParagraphBlock> blocks = new ArrayList<>();
        for (JsonNode node : paragraphNodes) {
            String type = node.path("type").asText("");
            String text = node.path("text").asText("");
            switch (type) {
                case "IMG" -> {
                    String imageId = node.path("metadata").path("id").asText(null);
                    if (imageId != null && !imageId.isBlank()) {
                        // Medium stores a figure's caption (e.g. "Fig 1") as
                        // this same paragraph's own text field -- confirmed
                        // against a real captioned image. Reusing renderedText
                        // for it, same as every other paragraph type.
                        String caption = applyMarkups(text, node.path("markups"));
                        blocks.add(new ParagraphBlock(type, caption.isBlank() ? null : caption, imageId, null, null));
                    }
                }
                case "IFRAME", "MIXTAPE_EMBED" -> {
                    String href = node.path("iframe").path("mediaResource").path("href").asText(null);
                    if (href == null || href.isBlank()) {
                        href = node.path("mixtapeMetadata").path("href").asText(null);
                    }
                    if (href != null && !href.isBlank()) {
                        blocks.add(new ParagraphBlock(type, null, null, href, null));
                    }
                }
                case "PRE", "CODE_BLOCK" -> {
                    String lang = node.path("codeBlockMetadata").path("lang").asText(null);
                    blocks.add(new ParagraphBlock(type, applyMarkups(text, node.path("markups")), null, null, lang));
                }
                case "H3", "H4", "P", "BQ", "PQ", "ULI", "OLI" ->
                        blocks.add(new ParagraphBlock(type, applyMarkups(text, node.path("markups")), null, null, null));
                default -> {
                    if (!text.isBlank()) {
                        warnings.add("Skipped unrecognized paragraph type: " + type);
                        blocks.add(new ParagraphBlock(type, applyMarkups(text, node.path("markups")), null, null, null));
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

            // Medium's recorded start/end sometimes includes the word's
            // trailing (or leading) space -- e.g. a STRONG span covering
            // "New Project " (with the space) rather than "New Project".
            // Inserting "**" right at that raw `end` then puts the closing
            // delimiter after whitespace, which CommonMark's flanking rule
            // for emphasis/strong explicitly disallows as a closer -- remark
            // leaves it as two literal asterisks instead of rendering bold.
            // Confirmed against a real published post: "**SAVE AND CONTINUE
            // **button" rendered as literal asterisks around plain text.
            // Trimming the span to its actual non-whitespace content first
            // keeps the delimiters directly against real characters, so
            // markdown parses it as intended either way.
            int start = m.start();
            int end = m.end();
            while (start < end && Character.isWhitespace(text.charAt(start))) start++;
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
            if (start >= end) continue;

            sb.insert(end, suffix);
            sb.insert(start, prefix);
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
            case "PRE", "CODE_BLOCK" -> "```" + (block.codeLang() != null ? block.codeLang() : "")
                    + "\n" + block.renderedText() + "\n```";
            case "ULI" -> "- " + block.renderedText();
            case "OLI" -> "1. " + block.renderedText();
            case "IFRAME", "MIXTAPE_EMBED" -> block.embedHref() != null
                    ? "[Embedded content](" + block.embedHref() + ")" : null;
            case "IMG" -> null;
            default -> block.renderedText();
        };
    }

    // Builds the markdown for a successfully-downloaded image, including its
    // caption if Medium had one. The caption becomes both the image's alt
    // text and a separate visible italic line below it -- alt text alone
    // isn't rendered under a working image by any Markdown viewer (only
    // shown if the image fails to load), so relying on it alone would still
    // lose the caption's visibility even though the text is technically
    // preserved. Pure/static like the rest of this class -- the caller
    // (MediumImportService) supplies the already-downloaded url once it has one.
    public static String imageMarkdownWithCaption(String url, String caption) {
        String altText = caption != null ? sanitizeAltText(caption) : "";
        String markdown = "![" + altText + "](" + url + ")";
        if (caption != null && !caption.isBlank()) {
            markdown += "\n\n*" + caption + "*";
        }
        return markdown;
    }

    // Markdown alt text lives inside [...], so a literal ']' would
    // prematurely close it; a literal newline would break the image syntax
    // onto multiple lines. Medium captions are short plain text in practice,
    // so this is a minimal safety net, not a full markdown-escaping pass.
    private static String sanitizeAltText(String text) {
        return text.replace("[", "(").replace("]", ")").replace("\n", " ").trim();
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
