package com.example.demo.service;

import com.example.demo.endpoint.rest.dto.ImageSubmissionResponse;
import java.io.File;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@AllArgsConstructor
public class ImageSubmissionService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
  private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

  private final JdbcTemplate jdbcTemplate;

  @SneakyThrows
  @Transactional
  public ImageSubmissionResponse submit(String email, MultipartFile image) {
    validate(image);

    var id = UUID.randomUUID();
    var now = OffsetDateTime.now();
    var extension = "image/png".equals(image.getContentType()) ? ".png" : ".jpg";
    var originalKey = "originals/" + id + extension;

    // TODO: upload du fichier vers S3 ici (BucketComponent), une fois que la
    // partie stockage/fichier sera branchée. Pour l'instant on garde juste la clé.

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
                            .createdAt(rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC))
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
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (max 5 Mo)");
    }
  }
}