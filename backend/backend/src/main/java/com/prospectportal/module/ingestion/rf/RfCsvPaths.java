package com.prospectportal.module.ingestion.rf;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class RfCsvPaths {

    private RfCsvPaths() {
    }

    public static final Charset RF_CHARSET = Charset.forName("ISO-8859-1");

    public static Path findExtractedFile(Path extractedRoot, String folderPrefix, String fileNameContains) throws Exception {
        if (!Files.isDirectory(extractedRoot)) {
            throw new IllegalStateException("Pasta não encontrada: " + extractedRoot);
        }
        try (Stream<Path> walk = Files.walk(extractedRoot, 4)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toUpperCase().contains(fileNameContains.toUpperCase()))
                .filter(path -> path.toString().replace('\\', '/').contains("/" + folderPrefix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "Arquivo não encontrado em " + extractedRoot + " (pasta " + folderPrefix + ", contém " + fileNameContains + ")"
                ));
        }
    }

    public static String field(String[] cols, int index) {
        if (index >= cols.length) {
            return "";
        }
        if (cols[index] == null) {
            return "";
        }
        String value = cols[index].trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    public static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
