package com.spmf.music;

import jakarta.enterprise.context.ApplicationScoped;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;

@ApplicationScoped
public class MusicMetadataService {

    public MusicMetadata read(File file) {

        MusicMetadata result =
                new MusicMetadata();

        try {

            AudioFile audio =
                    AudioFileIO.read(file);

            Tag tag =
                    audio.getTag();

            if (tag != null) {

                result.title =
                        tag.getFirst(FieldKey.TITLE);

                result.artist =
                        tag.getFirst(FieldKey.ARTIST);

                result.album =
                        tag.getFirst(FieldKey.ALBUM);

                try {

                    String track =
                            tag.getFirst(
                                    FieldKey.TRACK
                            );

                    if (
                            track != null &&
                                    !track.isBlank()
                    ) {

                        result.trackNumber =
                                Integer.parseInt(
                                        track.split("/")[0]
                                );
                    }

                } catch (Exception ignored) {
                }
            }

            result.durationMs =
                    audio.getAudioHeader()
                            .getTrackLength()
                            * 1000L;

        } catch (Exception e) {

            e.printStackTrace();
        }

        return result;
    }

    public String extractArtwork(
            File musicFile,
            Long mediaId
    ) {

        try {

            AudioFile audio =
                    AudioFileIO.read(
                            musicFile
                    );

            Tag tag =
                    audio.getTag();

            if (tag == null) {
                return null;
            }

            if (
                    tag.getFirstArtwork()
                            == null
            ) {
                return null;
            }

            byte[] image =
                    tag.getFirstArtwork()
                            .getBinaryData();

            String output =
                    "G:/SPMF/media/music/music-art/"
                            + mediaId
                            + ".jpg";

            Files.write(
                    Path.of(output),
                    image
            );

            return "/music-art/"
                    + mediaId
                    + ".jpg";

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }
    }
}