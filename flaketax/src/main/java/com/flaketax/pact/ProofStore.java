package com.flaketax.pact;

import com.flaketax.web.ApiErrors.Fail;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ProofStore {
    private final Path folder;
    public ProofStore(@Value("${flaketax.proof-dir:./data/proofs}") String folder) {
        this.folder = Path.of(folder).toAbsolutePath().normalize();
    }

    public Saved save(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) return null;
        if (upload.getSize() > 2_000_000)
            throw new Fail(HttpStatus.BAD_REQUEST, "Photo must be under 2 MB");
        try {
            byte[] bytes = upload.getBytes();
            String mime = imageType(bytes);
            if (mime == null)
                throw new Fail(HttpStatus.BAD_REQUEST, "Use a JPEG, PNG or WebP image");
            String key = UUID.randomUUID().toString().replace("-", "");
            Files.createDirectories(folder);
            Files.write(folder.resolve(key), bytes, StandardOpenOption.CREATE_NEW);
            return new Saved(key, mime);
        } catch (IOException ex) {
            throw new Fail(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save photo");
        }
    }

    public byte[] read(String key) {
        if (key == null || !key.matches("[a-f0-9]{32}"))
            throw new Fail(HttpStatus.NOT_FOUND, "Photo not found");
        try {
            return Files.readAllBytes(folder.resolve(key));
        } catch (IOException ex) {
            throw new Fail(HttpStatus.NOT_FOUND, "Photo not found");
        }
    }

    private String imageType(byte[] b) {
        if (b.length >= 8 && (b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71 &&
                b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10) return "image/png";
        if (b.length >= 3 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) return "image/jpeg";
        if (b.length >= 12 && b[0]==82 && b[1]==73 && b[2]==70 && b[3]==70 &&
                b[8]==87 && b[9]==69 && b[10]==66 && b[11]==80) return "image/webp";
        return null;
    }
    public record Saved(String key, String type) {}
}