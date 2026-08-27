package com.enterprise.knowledge.document.extraction;

import com.enterprise.knowledge.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chooses the right {@link TextExtractor} for a file and applies it.
 *
 * Format is decided by file extension rather than by the browser-supplied
 * content type, because that header is trivially forged and inconsistent across
 * clients. The extension is checked against an allowlist, so an unrecognised
 * type is rejected rather than guessed at.
 */
@Component
public class DocumentTextExtractor {

    private final Map<String, TextExtractor> extractorsByExtension;

    public DocumentTextExtractor(List<TextExtractor> extractors) {
        this.extractorsByExtension = extractors.stream().collect(Collectors.toMap(
                TextExtractor::extension,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    public Set<String> supportedExtensions() {
        return extractorsByExtension.keySet();
    }

    public boolean supports(String fileName) {
        return extractorsByExtension.containsKey(extensionOf(fileName));
    }

    /**
     * The content type to record for a file, derived from its extension so that
     * the stored value is consistent regardless of what the client sent.
     */
    public String contentTypeFor(String fileName) {
        return requireExtractor(fileName).contentType();
    }

    public ExtractionResult extract(Path file, String fileName) {
        return requireExtractor(fileName).extract(file);
    }

    private TextExtractor requireExtractor(String fileName) {
        TextExtractor extractor = extractorsByExtension.get(extensionOf(fileName));
        if (extractor == null) {
            throw new BadRequestException(
                    "Unsupported file type. Supported types are: "
                            + String.join(", ", supportedExtensions()) + ".");
        }
        return extractor;
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
