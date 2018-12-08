package org.dynamicruntime.util;

import org.dynamicruntime.exception.DnException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;

public class IoUtil {
    public static byte[] readInFile(File file) throws DnException {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            int code = (e instanceof FileNotFoundException) ? DnException.NOT_FOUND :
                    DnException.INTERNAL_ERROR;
            throw DnException.mkFileIo(String.format("Cannot access file %s.",
                    file.toString()), e, code);
        }
    }
}
