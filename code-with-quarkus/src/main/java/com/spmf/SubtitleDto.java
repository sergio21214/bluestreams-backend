package com.spmf;

public class SubtitleDto {

    public String file;

    public String label;

    public String lang;

    public SubtitleDto() {
    }

    public SubtitleDto(
            String file,
            String label,
            String lang
    ) {

        this.file = file;
        this.label = label;
        this.lang = lang;
    }


}