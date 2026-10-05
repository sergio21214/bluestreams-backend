package com.spmf.auth;

import java.security.SecureRandom;
import java.util.Base64;

public class RefreshTokenUtil {

    private static final SecureRandom random =
            new SecureRandom();

    public static String generate() {

        byte[] bytes = new byte[64];

        random.nextBytes(bytes);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

    }

}