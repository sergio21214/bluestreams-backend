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

    @ConfigProperty(
            name = "ytdlp.plugin-dirs",
            defaultValue = "/root/yt-dlp-plugins"
    )
    String pluginDirs;

    public List<String> commonArgs() {

        List<String> args = new ArrayList<>();

        if (
                pluginDirs != null
                        && !pluginDirs.isBlank()
                        && Files.isDirectory(Path.of(pluginDirs))
        ) {
            args.add("--plugin-dirs");
            args.add(pluginDirs);
        }

        args.add("--extractor-args");
        args.add("youtube:player_client=" + playerClient);

        if (potBaseUrl != null && !potBaseUrl.isBlank()) {
            args.add("--extractor-args");
            args.add("youtubepot-bgutilhttp:base_url=" + potBaseUrl);
        }

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
