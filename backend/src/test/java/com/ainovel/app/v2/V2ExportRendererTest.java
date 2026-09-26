package com.ainovel.app.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class V2ExportRendererTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"LF", "CRLF"})
    void txtBytesPreserveOutlineOrderChineseParagraphsAndEntities(String lineEnding) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String outline = mapper.writeValueAsString(Map.of("chapters", List.of(
                Map.of("title", "第一章 来信", "scenes", List.of(Map.of("id", "z", "title", "邮戳"))),
                Map.of("title", "第二章 代价", "scenes", List.of(Map.of("id", "a", "title", "记忆"))))));
        String sections = mapper.writeValueAsString(Map.of(
                "a", "<p>她记得那根蓝线。</p>",
                "z", "<p>林砚拆信。&lt;明日&gt;&amp;旧邮局</p><p>他说：&quot;别去。&quot;<br />窗外下雨。&#x4FE1;</p>"));
        String snapshot = mapper.writeValueAsString(Map.of("title", "明日来信", "outline", outline, "sections", sections));
        Path file = directory.resolve("明日来信.txt");
        new V2ExportRenderer(mapper).render("txt", snapshot,
                mapper.writeValueAsString(Map.of("includeMetadata", false, "lineEnding", lineEnding)), "all", file);
        String expected = "第一章 来信\n\n邮戳\n\n林砚拆信。<明日>&旧邮局\n\n他说：\"别去。\"\n窗外下雨。信\n\n第二章 代价\n\n记忆\n\n她记得那根蓝线。";
        if (lineEnding.equals("CRLF")) expected = expected.replace("\n", "\r\n");
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
    }
}
