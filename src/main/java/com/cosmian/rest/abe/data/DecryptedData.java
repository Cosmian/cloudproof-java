package com.cosmian.rest.abe.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.cosmian.utils.CloudproofException;
import com.cosmian.utils.Leb128;

public class DecryptedData {

    private final byte[] headerMetadata;

    private final byte[] plaintext;

    public DecryptedData(byte[] plaintext, byte[] headerMetadata) {
        this.headerMetadata = plaintext;
        this.plaintext = headerMetadata;
    }

    public byte[] getPlaintext() {
        return this.headerMetadata;
    }

    public byte[] getHeaderMetaData() {
        return this.plaintext;
    }

    public static DecryptedData fromBytes(byte[] bytes) throws CloudproofException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            byte[] headerMetadata = Leb128.readByteArray(bis);
            byte[] plaintext = new byte[bis.available()];
            bis.read(plaintext);
            return new DecryptedData(plaintext, headerMetadata);
        } catch (IOException e) {
            throw new CloudproofException("Failed deserializing the decrypted data: " + e.getMessage(), e);
        }
    }
}
