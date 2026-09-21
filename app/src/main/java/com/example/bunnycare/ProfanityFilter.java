package com.example.bunnycare;

public class ProfanityFilter {

    private static final String[] BAD_WORDS = {
            "fuck",
            "shit",
            "bitch",
            "asshole",
            "damn",
            "motherfucker",
            "bastard",
    };

    public static String filter(String text) {

        if (text == null) {
            return "";
        }

        String filtered = text;

        for (String word : BAD_WORDS) {

            String regex =
                    "(?i)\\b"
                            + word
                            + "\\b";

            String replacement =
                    new String(
                            new char[word.length()]
                    ).replace('\0', '*');

            filtered =
                    filtered.replaceAll(
                            regex,
                            replacement
                    );
        }

        return filtered;
    }
}