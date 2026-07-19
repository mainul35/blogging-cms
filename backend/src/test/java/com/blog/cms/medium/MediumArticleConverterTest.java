package com.blog.cms.medium;

import com.blog.cms.medium.MediumArticleConverter.ParagraphBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Plain JUnit, no Spring context -- MediumArticleConverter has zero
// Spring/reactive/I/O dependencies on purpose, so the whole parse/convert
// pipeline is exercisable here with a hand-built fixture. This fixture's
// shape (HTML page with an embedded window.__APOLLO_STATE__ normalized
// cache, paragraphs stored as {"__ref": "Paragraph:<id>"} pointers into a
// flat "TypeName:id" -> object map) was confirmed against a real Medium
// article fetch; field names/values below are synthetic, not the real
// article's content.
class MediumArticleConverterTest {

    private static final String POST_ID = "abc123def456";

    private static final String FIXTURE_HTML = """
            <!doctype html><html><head><title>Test</title></head><body>
            <div id="root"></div>
            <script>window.__APOLLO_STATE__ = {"Post:abc123def456":{
              "__typename":"Post",
              "title":"Test Article",
              "extendedPreviewContent":{"__typename":"PreviewContent","subtitle":"A subtitle"},
              "previewImage":{"__typename":"ImageMetadata","id":"1*cover.png"},
              "content({\\"postMeteringOptions\\":{\\"referrer\\":\\"\\"}})":{
                "__typename":"PostContent",
                "bodyModel":{"__typename":"RichText","paragraphs":[
                  {"__ref":"Paragraph:p_0"},
                  {"__ref":"Paragraph:p_1"},
                  {"__ref":"Paragraph:p_2"},
                  {"__ref":"Paragraph:p_3"},
                  {"__ref":"Paragraph:p_4"},
                  {"__ref":"Paragraph:p_5"},
                  {"__ref":"Paragraph:p_missing"}
                ]}
              }
            },
            "Paragraph:p_0":{"__typename":"Paragraph","type":"H3","text":"Heading","markups":[]},
            "Paragraph:p_1":{"__typename":"Paragraph","type":"P","text":"Some bold and a link.","markups":[
              {"__typename":"Markup","type":"STRONG","start":5,"end":9},
              {"__typename":"Markup","type":"A","start":16,"end":20,"href":"https://example.com"}]},
            "Paragraph:p_2":{"__typename":"Paragraph","type":"IMG","text":"","metadata":{"__typename":"ImageMetadata","id":"1*abc123.png"}},
            "Paragraph:p_3":{"__typename":"Paragraph","type":"ULI","text":"item one","markups":[]},
            "Paragraph:p_4":{"__typename":"Paragraph","type":"ULI","text":"item two","markups":[]},
            "Paragraph:p_5":{"__typename":"Paragraph","type":"PRE","text":"echo hello","markups":[],"codeBlockMetadata":{"__typename":"CodeBlockMetadata","mode":"EXPLICIT","lang":"shell"}}
            };</script>
            <script>window.__OTHER_STATE__ = {"unrelated": true};</script>
            </body></html>""";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractApolloStateJson_findsTheRightScriptBlock() throws Exception {
        String json = MediumArticleConverter.extractApolloStateJson(FIXTURE_HTML);
        JsonNode root = objectMapper.readTree(json);
        assertThat(root.has("Post:abc123def456")).isTrue();
        assertThat(root.has("unrelated")).isFalse();
    }

    @Test
    void extractApolloStateJson_throwsWhenVariableNotPresent() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MediumArticleConverter.extractApolloStateJson("<html><body>no state here</body></html>"));
    }

    @ParameterizedTest
    @CsvSource({
            "'https://mainul35.medium.com/complete-guide-to-docker-d6aa51936735', d6aa51936735",
            "'https://medium.com/@someone/a-title-abc123def456', abc123def456",
            "'https://medium.com/@someone/a-title-abc123def456/', abc123def456",
    })
    void extractPostIdFromUrl_findsTrailingHexSegment(String url, String expectedId) {
        assertThat(MediumArticleConverter.extractPostIdFromUrl(url)).isEqualTo(expectedId);
    }

    @Test
    void extractPostIdFromUrl_throwsWhenNoHexSegmentPresent() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MediumArticleConverter.extractPostIdFromUrl("https://medium.com/@someone/just-words"));
    }

    @Test
    void normalizeFetchUrl_passesThroughABareUrlUnchanged() {
        assertThat(MediumArticleConverter.normalizeFetchUrl("https://mainul35.medium.com/a-title-d6aa51936735"))
                .isEqualTo("https://mainul35.medium.com/a-title-d6aa51936735");
    }

    @Test
    void normalizeFetchUrl_extractsUrlFromDevToolsCopyAsFetchSnippet() {
        // A trimmed-down version of what Chrome/Edge DevTools' "Copy as fetch"
        // context-menu option actually produces -- the real trigger for this:
        // an admin pasted exactly this shape and got a generic "Invalid fetch
        // URL" error, since the raw string starts with `fetch("` rather than
        // being a bare URL.
        String snippet = """
                fetch("https://mainul35.medium.com/a-title-d6aa51936735", {
                  "headers": {
                    "accept": "text/html,application/xhtml+xml",
                    "sec-fetch-dest": "document"
                  },
                  "body": null,
                  "method": "GET",
                  "mode": "cors",
                  "credentials": "include"
                });""";

        assertThat(MediumArticleConverter.normalizeFetchUrl(snippet))
                .isEqualTo("https://mainul35.medium.com/a-title-d6aa51936735");
    }

    @Test
    void normalizeFetchUrl_handlesSingleQuotesAndLeadingWhitespace() {
        assertThat(MediumArticleConverter.normalizeFetchUrl("  fetch('https://medium.com/@x/y-abc123', {})"))
                .isEqualTo("https://medium.com/@x/y-abc123");
    }

    @Test
    void findPost_resolvesByIdAndThrowsWhenMissing() throws Exception {
        JsonNode state = parseFixtureState();
        JsonNode post = MediumArticleConverter.findPost(state, POST_ID);
        assertThat(post.path("title").asText()).isEqualTo("Test Article");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MediumArticleConverter.findPost(state, "nonexistent"));
    }

    @Test
    void findContent_matchesFieldByPrefixRegardlessOfArguments() throws Exception {
        JsonNode state = parseFixtureState();
        JsonNode post = MediumArticleConverter.findPost(state, POST_ID);
        JsonNode content = MediumArticleConverter.findContent(post);
        assertThat(content.path("__typename").asText()).isEqualTo("PostContent");
        assertThat(content.path("bodyModel").path("paragraphs").isArray()).isTrue();
    }

    @Test
    void resolveParagraphRefs_resolvesKnownRefsAndWarnsOnMissingOnes() throws Exception {
        JsonNode state = parseFixtureState();
        JsonNode post = MediumArticleConverter.findPost(state, POST_ID);
        JsonNode content = MediumArticleConverter.findContent(post);
        JsonNode refs = content.path("bodyModel").path("paragraphs");
        List<String> warnings = new ArrayList<>();

        List<JsonNode> resolved = MediumArticleConverter.resolveParagraphRefs(refs, state, warnings);

        assertThat(resolved).hasSize(6); // 7 refs, 1 unresolvable
        assertThat(warnings).containsExactly("Could not resolve paragraph reference: Paragraph:p_missing");
    }

    @Test
    void applyMarkups_insertsBoldAndLinkAtCorrectOffsets() throws Exception {
        JsonNode state = parseFixtureState();
        JsonNode paragraph = state.path("Paragraph:p_1");
        String rendered = MediumArticleConverter.applyMarkups(
                paragraph.path("text").asText(), paragraph.path("markups"));
        assertThat(rendered).isEqualTo("Some **bold** and a [link](https://example.com).");
    }

    @Test
    void parseParagraphs_mapsKnownTypesIncludingCodeBlockLanguage() throws Exception {
        JsonNode state = parseFixtureState();
        JsonNode post = MediumArticleConverter.findPost(state, POST_ID);
        JsonNode refs = MediumArticleConverter.findContent(post).path("bodyModel").path("paragraphs");
        List<String> warnings = new ArrayList<>();
        List<JsonNode> resolved = MediumArticleConverter.resolveParagraphRefs(refs, state, warnings);

        List<ParagraphBlock> blocks = MediumArticleConverter.parseParagraphs(resolved, warnings);

        assertThat(blocks).hasSize(6);
        assertThat(blocks.get(0).type()).isEqualTo("H3");
        assertThat(blocks.get(2).type()).isEqualTo("IMG");
        assertThat(blocks.get(2).imageId()).isEqualTo("1*abc123.png");
        assertThat(blocks.get(5).type()).isEqualTo("PRE");
        assertThat(blocks.get(5).codeLang()).isEqualTo("shell");
        assertThat(MediumArticleConverter.markdownFor(blocks.get(5))).isEqualTo("```shell\necho hello\n```");
    }

    @Test
    void assembleMarkdown_joinsConsecutiveListItemsWithSingleNewline() {
        List<String> md = List.of("### Heading", "- item one", "- item two", "Some text");
        List<String> types = List.of("H3", "ULI", "ULI", "P");

        String result = MediumArticleConverter.assembleMarkdown(md, types);

        assertThat(result).isEqualTo("### Heading\n\n- item one\n- item two\n\nSome text");
    }

    @Test
    void assembleMarkdown_skipsNullOrBlankBlocks() {
        List<String> md = new ArrayList<>(List.of("### Heading", "", "Body text"));
        List<String> types = List.of("H3", "IMG", "P");

        String result = MediumArticleConverter.assembleMarkdown(md, types);

        assertThat(result).isEqualTo("### Heading\n\nBody text");
    }

    @Test
    void fullFixture_endToEndConversionProducesExpectedMarkdown() throws Exception {
        JsonNode state = parseFixtureState();
        JsonNode post = MediumArticleConverter.findPost(state, POST_ID);
        JsonNode refs = MediumArticleConverter.findContent(post).path("bodyModel").path("paragraphs");
        List<String> warnings = new ArrayList<>();
        List<JsonNode> resolved = MediumArticleConverter.resolveParagraphRefs(refs, state, warnings);
        List<ParagraphBlock> blocks = MediumArticleConverter.parseParagraphs(resolved, warnings);

        List<String> blockMarkdown = new ArrayList<>();
        List<String> blockTypes = new ArrayList<>();
        for (ParagraphBlock block : blocks) {
            if ("IMG".equals(block.type())) {
                blockMarkdown.add("![](/uploads/fake.png)"); // simulates a resolved image download
            } else {
                blockMarkdown.add(MediumArticleConverter.markdownFor(block));
            }
            blockTypes.add(block.type());
        }
        String markdown = MediumArticleConverter.assembleMarkdown(blockMarkdown, blockTypes);

        assertThat(markdown).isEqualTo(
                "### Heading\n\n"
                        + "Some **bold** and a [link](https://example.com).\n\n"
                        + "![](/uploads/fake.png)\n\n"
                        + "- item one\n- item two\n\n"
                        + "```shell\necho hello\n```");
        assertThat(warnings).containsExactly("Could not resolve paragraph reference: Paragraph:p_missing");
    }

    @ParameterizedTest
    @CsvSource({
            "medium.com, true",
            "sub.medium.com, true",
            "a.b.medium.com, true",
            "evil.com, false",
            "medium.com.evil.com, false",
            "notmedium.com, false",
    })
    void isAllowedFetchHost_onlyAllowsMediumComAndSubdomains(String host, boolean expected) {
        assertThat(MediumArticleConverter.isAllowedFetchHost(host)).isEqualTo(expected);
    }

    @Test
    void isAllowedFetchHost_rejectsNullHost() {
        assertThat(MediumArticleConverter.isAllowedFetchHost(null)).isFalse();
    }

    private JsonNode parseFixtureState() throws Exception {
        return objectMapper.readTree(MediumArticleConverter.extractApolloStateJson(FIXTURE_HTML));
    }
}
