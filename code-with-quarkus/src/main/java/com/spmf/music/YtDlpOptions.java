package com.spmf.music;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class YtDlpOptions {

    @ConfigProperty(name = "ytdlp.player-client", defaultValue = "web_embedded")
    String playerClient;

    @ConfigProperty(
            name = "ytdlp.pot-base-url",
            defaultValue = "http://bgutil-provider:4416"
    )
    String potBaseUrl;

    @ConfigProperty(name = "ytdlp.cookies-file")
    Optional<String> cookiesFile;

    public List<String> commonArgs() {

        List<String> args = new ArrayList<>();

        StringBuilder extractorArgs =
                new StringBuilder("youtube:player_client=")
                        .append(playerClient);

        if (potBaseUrl != null && !potBaseUrl.isBlank()) {
            extractorArgs
                    .append(";getpot_bgutil_baseurl=")
                    .append(potBaseUrl);
        }

        args.add("--extractor-args");
        args.add(extractorArgs.toString());

        cookiesFile
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .filter(Files::isReadable)
                .ifPresent(path -> {
                    args.add("--cookies");
                    args.add(path.toString());
                });

        return args;
    }
}
