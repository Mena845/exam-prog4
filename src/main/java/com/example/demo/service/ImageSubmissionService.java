package com.example.demo.service;

import com.example.demo.endpoint.event.EventProducer;
import com.example.demo.endpoint.event.model.ImageBwConversionRequested;
import com.example.demo.endpoint.rest.dto.ImageSubmissionResponse;
import com.example.demo.file.bucket.BucketComponent;
import com.example.demo.mail.Email;
import com.example.demo.mail.Mailer;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@AllArgsConstructor
@Slf4j
public class ImageSubmissionService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
  private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

  private final JdbcTemplate jdbcTemplate;
  private final BucketComponent bucketComponent;
  private final EventProducer eventProducer;
  private final Mailer mailer;

  public ImageSubmissionResponse submit(String email, MultipartFile image) {
    validate(image);

    var id = UUID.randomUUID();
    var now = OffsetDateTime.now();
    var extension = "image/png".equals(image.getContentType()) ? ".png" : ".jpg";
    var originalKey = "originals/" + id + extension;

    File tempFile;
    try {
      tempFile = File.createTempFile("upload-" + id, extension);
      image.transferTo(tempFile);
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Échec de la lecture de l'image", e);
    }

    jdbcTemplate.update(
        "INSERT INTO image_submission "
            + "(id, original_filename, email, created_at, original_s3_key, status) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id,
        image.getOriginalFilename(),
        email,
        Timestamp.from(now.toInstant()),
        originalKey,
        "PENDING");

    var event = ImageBwConversionRequested.builder().imageId(id).originalS3Key(originalKey).build();
    try {
      eventProducer.accept(List.of(event));
    } catch (Exception e) {
      log.error("Failed to emit EventBridge event for image {}", id, e);
    }

    try {
      mailer.accept(
          new Email(
              new InternetAddress(email),
              List.of(),
              List.of(),
              "Votre image a été reçue",
              "<p>Bonjour,</p>"
                  + "<p>Votre image a été soumise avec succès et est en cours de conversion "
                  + "noir et blanc.</p>"
                  + "<p>Vous recevrez un autre email avec le lien de téléchargement une fois la "
                  + "conversion terminée.</p>",
              List.of()));
      log.info("Confirmation email sent to {} for imageId={}", email, id);
    } catch (Exception e) {
      log.error("Failed to send confirmation email for image {}", id, e);
    }

    java.util.concurrent.CompletableFuture.runAsync(
        () -> {
          try {
            bucketComponent.upload(tempFile, originalKey);
            log.info("Image uploaded to S3: key={}, size={}bytes", originalKey, image.getSize());
          } catch (Exception e) {
            log.error("Failed to upload image to S3: key={}", originalKey, e);
          } finally {
            tempFile.delete();
          }
        });

    return ImageSubmissionResponse.builder()
        .id(id)
        .filename(image.getOriginalFilename())
        .email(email)
        .createdAt(now)
        .status("PENDING")
        .bwImageUrl(null)
        .build();
  }

  public List<ImageSubmissionResponse> findAll() {
    return jdbcTemplate.query(
        "SELECT id, original_filename, email, created_at, status, bw_s3_key "
            + "FROM image_submission ORDER BY created_at DESC",
        (rs, rowNum) ->
            ImageSubmissionResponse.builder()
                .id(UUID.fromString(rs.getString("id")))
                .filename(rs.getString("original_filename"))
                .email(rs.getString("email"))
                .createdAt(
                    rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC))
                .status(rs.getString("status"))
                .bwImageUrl(rs.getString("bw_s3_key"))
                .build());
  }

  private void validate(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier image est requis");
    }
    if (!ALLOWED_CONTENT_TYPES.contains(image.getContentType())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Seuls les formats JPEG et PNG sont acceptés");
    }
    if (image.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (max 5 Mo)");
    }
  }
}
