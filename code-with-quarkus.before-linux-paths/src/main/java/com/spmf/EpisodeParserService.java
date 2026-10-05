package com.spmf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EpisodeParserService {

    public static ParsedEpisode parse(
            String filename
    ) {

        String clean = filename
                .replace(".mkv", "")
                .replace(".mp4", "");

        // S01E06
        Pattern sxe =
                Pattern.compile(
                        "S(\\d+)E(\\d+)",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher sxeMatcher =
                sxe.matcher(clean);

        if (sxeMatcher.find()) {

            int season =
                    Integer.parseInt(
                            sxeMatcher.group(1)
                    );

            int episode =
                    Integer.parseInt(
                            sxeMatcher.group(2)
                    );

            return new ParsedEpisode(
                    season,
                    episode
            );
        }

        // episode 6
        Pattern ep =
                Pattern.compile(
                        "episode\\s*(\\d+)",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher epMatcher =
                ep.matcher(clean);

        if (epMatcher.find()) {

            int episode =
                    Integer.parseInt(
                            epMatcher.group(1)
                    );

            return new ParsedEpisode(
                    1,
                    episode
            );
        }

        return new ParsedEpisode(
                1,
                1
        );
    }
}