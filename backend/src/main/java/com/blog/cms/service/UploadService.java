package com.blog.cms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UploadService {

    private static final String UPLOAD_URL_PREFIX = "/uploads/";
    private static final Pattern IMAGE_MARKDOWN_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(([^)\\s]+)\\)");

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("#{'${app.upload.allowed-types}'.split(',')}")
    private List<String> allowedTypes;

    @Value("${app.upload.max-size-mb}")
    private long maxSizeMb;

    public Mono<String> store(FilePart file) {
        String contentType = file.headers().getContentType() != null
                ? file.headers().getContentType().toString()
                : "";
        if (!allowedTypes.contains(contentType)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Allowed image types: " + String.join(", ", allowedTypes)));
        }

        long contentLength = file.headers().getContentLength();
        if (contentLength > maxSizeMb * 1024 * 1024) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Image must be " + maxSizeMb + "MB or smaller"));
        }

        String extension = extensionFor(contentType);
        String filename = UUID.randomUUID() + extension;

        return Mono.fromCallable(() -> {
                    Path dir = Paths.get(uploadDir);
                    Files.createDirectories(dir);
                    return dir.resolve(filename);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> file.transferTo(path).thenReturn(UPLOAD_URL_PREFIX + filename));
    }

    /** Filenames of our own uploaded images referenced by this post's content and cover image. */
    public Set<String> extractLocalFilenames(String content, String coverImageUrl) {
        Set<String> filenames = new HashSet<>();
        if (content != null) {
            Matcher matcher = IMAGE_MARKDOWN_PATTERN.matcher(content);
            while (matcher.find()) {
                addIfLocal(filenames, matcher.group(1));
            }
        }
        addIfLocal(filenames, coverImageUrl);
        return filenames;
    }

    /** Best-effort disk cleanup; a missing or locked file should never fail the calling request. */
    public Mono<Void> deleteFiles(Set<String> filenames) {
        if (filenames.isEmpty()) return Mono.empty();
        return Mono.fromRunnable(() -> {
                    Path base = Paths.get(uploadDir).normalize().toAbsolutePath();
                    for (String filename : filenames) {
                        try {
                            Path path = base.resolve(filename).normalize();
                            if (!path.startsWith(base)) continue;
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void addIfLocal(Set<String> filenames, String url) {
        if (url == null) return;
        int idx = url.indexOf(UPLOAD_URL_PREFIX);
        if (idx == -1) return;
        String filename = url.substring(idx + UPLOAD_URL_PREFIX.length());
        if (!filename.isEmpty() && !filename.contains("/") && !filename.contains("\\") && !filename.contains("..")) {
            filenames.add(filename);
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
