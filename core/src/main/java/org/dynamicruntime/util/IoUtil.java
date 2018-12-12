package org.dynamicruntime.util;

import org.dynamicruntime.exception.DnException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@SuppressWarnings("WeakerAccess")
public class IoUtil {
    public static final ClassLoader classLoader = IoUtil.class.getClassLoader();

    public static byte[] readInBinaryFile(File file) throws DnException {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            int code = (e instanceof FileNotFoundException) ? DnException.NOT_FOUND :
                    DnException.INTERNAL_ERROR;
            throw DnException.mkFileIo(String.format("Cannot access file %s.",
                    file.toString()), e, code);
        }
    }

    public static String readInFile(File file) throws DnException {
        byte[] bytes = readInBinaryFile(file);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String readInResource(String resPath) throws DnException {
        if (resPath == null) {
            return null;
        }
        var resourceUrl = classLoader.getResource(resPath);
        if (resourceUrl == null) {
            return null;
        }
        File f = new File(resourceUrl.getFile());
        if (f.exists()) {
            return IoUtil.readInFile(f);
        }
        return null;
    }
}
