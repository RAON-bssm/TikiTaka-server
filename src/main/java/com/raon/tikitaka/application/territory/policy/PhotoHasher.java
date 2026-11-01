package com.raon.tikitaka.application.territory.policy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 사진 재사용 검사용 SHA-256. 같은 파일이면 같은 값이다(다시 찍은 사진은 다른 값) */
public final class PhotoHasher {

    private PhotoHasher() {
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
