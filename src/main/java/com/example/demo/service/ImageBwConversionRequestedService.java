package com.example.demo.service;

import static java.io.File.createTempFile;

import com.example.demo.endpoint.event.model.ImageBwConversionRequested;
import com.example.demo.file.bucket.BucketComponent;
import jakarta.mail.internet.InternetAddress;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class ImageBwConversionRequestedService implements Consumer<ImageBwConversionRequested> {

  private final BucketComponent bucketComponent;
  private final JdbcTemplate jdbcTemplate;
  private final com.example.demo.mail.Mailer mailer;

  @SneakyThrows
  @Override
  public void accept(ImageBwConversionRequested event) {
    log.info(
        "Starting BW conversion for imageId={}, originalS3Key={}",
        event.getImageId(),
        event.getOriginalS3Key());
    var extension = event.getOriginalS3Key().substring(event.getOriginalS3Key().lastIndexOf('.'));

    File original = bucketComponent.download(event.getOriginalS3Key());
    File bwFile = createTempFile("bw-" + event.getImageId(), extension);
    try {
      convertToGrayscale(original, bwFile);

      var bwKey = "bw/" + event.getImageId() + extension;
      bucketComponent.upload(bwFile, bwKey);

      jdbcTemplate.update(
          "UPDATE image_submission SET bw_s3_key = ?, status = 'DONE' WHERE id = ?",
          bwKey,
          event.getImageId());
      log.info("BW conversion completed for imageId={}", event.getImageId());

      var rows =
          jdbcTemplate.queryForList(
              "SELECT email FROM image_submission WHERE id = ?", event.getImageId());
      if (!rows.isEmpty()) {
        var userEmail = (String) rows.get(0).get("email");
        var presignedUrl = bucketComponent.presign(bwKey, Duration.ofHours(1));
        try {
          mailer.accept(
              new com.example.demo.mail.Email(
                  new InternetAddress(userEmail),
                  List.of(),
                  List.of(),
                  "Votre image convertie en noir et blanc est prête",
                  "<p>Bonjour,</p>"
                      + "<p>Votre image a été convertie en noir et blanc.</p>"
                      + "<p><a href=\""
                      + presignedUrl
                      + "\">Télécharger l'image BW</a></p>"
                      + "<p>Ce lien expire dans 1 heure.</p>",
                  List.of()));
          log.info("Email sent to {} for imageId={}", userEmail, event.getImageId());
        } catch (Exception e) {
          jdbcTemplate.update(
              "UPDATE image_submission SET error_message = ? WHERE id = ?",
              e.getMessage(),
              event.getImageId());
          throw e;
        }
      }
    } finally {
      original.delete();
      bwFile.delete();
    }
  }

  private void convertToGrayscale(File source, File target) throws Exception {
    BufferedImage original = ImageIO.read(source);
    BufferedImage grayscale =
        new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
    grayscale.getGraphics().drawImage(original, 0, 0, null);
    String format = target.getName().endsWith(".png") ? "png" : "jpg";
    ImageIO.write(grayscale, format, target);
  }
}
