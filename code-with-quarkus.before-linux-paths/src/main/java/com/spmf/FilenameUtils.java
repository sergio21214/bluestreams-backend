package com.spmf;

public class FilenameUtils {

    public static String cleanName(
            String filename
    ) {

        return filename
                .replace(".mp4", "")
                .replace(".mkv", "")
                .replaceAll(
                        "[^a-zA-Z0-9-_ ]",
                        ""
                );
    }
}