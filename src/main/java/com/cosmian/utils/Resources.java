package com.cosmian.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Resources {
    private static byte[] read_all_bytes(InputStream inputStream) throws IOException {
        final int BUFFER_LENGTH = 4096;
        byte[] buffer = new byte[BUFFER_LENGTH];
        int readLen;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        while ((readLen = inputStream.read(buffer, 0, BUFFER_LENGTH)) != -1)
            outputStream.write(buffer, 0, readLen);
        return outputStream.toByteArray();
    }

    public static byte[] load_resource_as_bytes(String resource_name) throws IOException {
        try (InputStream is = Resources.class.getClassLoader().getResourceAsStream(resource_name)) {
            return read_all_bytes(is);
        }
    }

    public static String load_resource(String resource_name) throws IOException {
        return new String(load_resource_as_bytes(resource_name), StandardCharsets.UTF_8);
    }

    public static String load_file(String file_path) throws IOException {
        try (InputStream is = new FileInputStream(file_path);) {
            return new String(read_all_bytes(is), StandardCharsets.UTF_8);
        }
    }

    public static void write_resource(String resource_name,
                                      byte[] bytes)
        throws IOException {
        String parentDir = Resources.class.getClassLoader().getResource(".").getFile();
        Path parentPath = Paths.get(new File(parentDir).getAbsolutePath(), resource_name);
        Files.createDirectories(parentPath.getParent());

        try (OutputStream os = new FileOutputStream(parentPath.toString())) {
            os.write(bytes);
            os.flush();
        }
    }
}
