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
// pipeline is exercisable here with a hand-built fixture, without needing
// a real Medium response.
class MediumArticleConverterTest {

    private static final String FIXTURE = """
            ])}while(1);</x>{"payload":{"value":{
              "title":"Test Article",
              "content":{"subtitle":"A subtitle",
                "bodyModel":{"paragraphs":[
                  {"type":"H3","text":"Heading","markups":[]},
                  {"type":"P","text":"Some bold and a link.","markups":[
                    {"type":"STRONG","start":5,"end":9},
                    {"type":"A","start":16,"end":20,"href":"https://example.com"}]},
                  {"type":"IMG","text":"","metadata":{"id":"1*abc123.png"}},
                  {"type":"ULI","text":"item one","markups":[]},
                  {"type":"ULI","text":"item two","markups":[]},
                  {"type":"WEIRD_FUTURE_TYPE","text":"fallback text","markups":[]}
                ]}},
              "virtuals":{"previewImage":{"id":"1*cover.png"}}
            }}}""";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stripXssiPrefix_findsFirstBrace() {
        String stripped = MediumArticleConverter.stripXssiPrefix(FIXTURE);
        assertThat(stripped).startsWith("{");
        assertThat(stripped).doesNotContain("while(1)");
    }

    @Test
    void stripXssiPrefix_throwsWhenNoJsonObjectPresent() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MediumArticleConverter.stripXssiPrefix("not json at all"));
    }

    @Test
    void extractPayloadValue_resolvesTitleAndSubtitle() throws Exception {
        JsonNode root = parseFixture();
        JsonNode value = MediumArticleConverter.extractPayloadValue(root);
        assertThat(value.path("title").asText()).isEqualTo("Test Article");
        assertThat(value.path("content").path("subtitle").asText()).isEqualTo("A subtitle");
    }

    @Test
    void extractPayloadValue_throwsWhenMissing() throws Exception {
        JsonNode root = objectMapper.readTree("{\"nothing\":\"here\"}");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MediumArticleConverter.extractPayloadValue(root));
    }

    @Test
    void applyMarkups_insertsBoldAndLinkAtCorrectOffsets() throws Exception {
        JsonNode root = parseFixture();
        JsonNode paragraphs = MediumArticleConverter.extractPayloadValue(root)
                .path("content").path("bodyModel").path("paragraphs");
        JsonNode boldLinkParagraph = paragraphs.get(1);
        String rendered = MediumArticleConverter.applyMarkups(
                boldLinkParagraph.path("text").asText(), boldLinkParagraph.path("markups"));
        assertThat(rendered).isEqualTo("Some **bold** and a [link](https://example.com).");
    }

    @Test
    void parseParagraphs_mapsKnownTypesAndFallsBackOnUnrecognizedType() throws Exception {
        JsonNode root = parseFixture();
        JsonNode paragraphsNode = MediumArticleConverter.extractPayloadValue(root)
                .path("content").path("bodyModel").path("paragraphs");
        List<String> warnings = new ArrayList<>();

        List<ParagraphBlock> blocks = MediumArticleConverter.parseParagraphs(paragraphsNode, warnings);

        assertThat(blocks).hasSize(6);
        assertThat(blocks.get(0).type()).isEqualTo("H3");
        assertThat(blocks.get(2).type()).isEqualTo("IMG");
        assertThat(blocks.get(2).imageId()).isEqualTo("1*abc123.png");
        assertThat(blocks.get(5).type()).isEqualTo("WEIRD_FUTURE_TYPE");
        assertThat(blocks.get(5).renderedText()).isEqualTo("fallback text");
        assertThat(warnings).containsExactly("Skipped unrecognized paragraph type: WEIRD_FUTURE_TYPE");
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
        JsonNode root = parseFixture();
        JsonNode value = MediumArticleConverter.extractPayloadValue(root);
        JsonNode paragraphsNode = value.path("content").path("bodyModel").path("paragraphs");
        List<String> warnings = new ArrayList<>();
        List<ParagraphBlock> blocks = MediumArticleConverter.parseParagraphs(paragraphsNode, warnings);

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
                        + "fallback text");
        assertThat(warnings).hasSize(1);
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

    private JsonNode parseFixture() throws Exception {
        return objectMapper.readTree(MediumArticleConverter.stripXssiPrefix(FIXTURE));
    }
}
