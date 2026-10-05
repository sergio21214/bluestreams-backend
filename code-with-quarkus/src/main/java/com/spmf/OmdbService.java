package com.spmf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class OmdbService {

    private static final String API_KEY = "9477d49a";

    public static void loadMetadata(Movie movie) {

        try {

            String encoded =
                    URLEncoder.encode(movie.title, "UTF-8");

            String urlString =
                    "http://www.omdbapi.com/"
                            + "?apikey=" + API_KEY
                            + "&t=" + encoded;

            URL url = new URL(urlString);

            HttpURLConnection connection =
                    (HttpURLConnection)
                            url.openConnection();

            connection.setRequestMethod("GET");

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getInputStream()
                            )
                    );

            StringBuilder response =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();

            ObjectMapper mapper =
                    new ObjectMapper();

            JsonNode root =
                    mapper.readTree(response.toString());

            if (root.has("Title")) {

                movie.title =
                        root.get("Title").asText();

                movie.overview =
                        root.get("Plot").asText();

                movie.poster =
                        root.get("Poster").asText();

                movie.genre =
                        root.get("Genre").asText();

                movie.year =
                        root.get("Year").asText();
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}