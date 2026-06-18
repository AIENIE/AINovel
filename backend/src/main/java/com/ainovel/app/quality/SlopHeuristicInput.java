package com.ainovel.app.quality;

public record SlopHeuristicInput(
        String text,
        String storyTitle,
        String genre,
        String tone,
        String chapterTitle,
        String sceneTitle,
        String characterContext,
        String styleContext
) {
    public SlopHeuristicInput {
        text = clean(text);
        storyTitle = clean(storyTitle);
        genre = clean(genre);
        tone = clean(tone);
        chapterTitle = clean(chapterTitle);
        sceneTitle = clean(sceneTitle);
        characterContext = clean(characterContext);
        styleContext = clean(styleContext);
    }

    public static SlopHeuristicInput textOnly(String text) {
        return new SlopHeuristicInput(text, "", "", "", "", "", "", "");
    }

    public static SlopHeuristicInput from(SlopQualityRequest request, String candidateText) {
        if (request == null) {
            return textOnly(candidateText);
        }
        return new SlopHeuristicInput(
                candidateText,
                request.storyTitle(),
                request.genre(),
                request.tone(),
                request.chapterTitle(),
                request.sceneTitle(),
                request.characterContext(),
                request.styleContext()
        );
    }

    public boolean hasContextHint(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        String combined = "%s %s %s %s %s %s".formatted(
                storyTitle,
                genre,
                tone,
                chapterTitle,
                sceneTitle,
                characterContext + " " + styleContext
        );
        return combined.contains(keyword);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
