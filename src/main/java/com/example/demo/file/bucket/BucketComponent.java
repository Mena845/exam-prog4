package com.example.demo.file.bucket;

import static java.io.File.createTempFile;

import com.example.demo.PojaGenerated;
import com.example.demo.file.hash.FileHash;
import com.example.demo.file.hash.FileHashAlgorithm;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@PojaGenerated
@Component
@AllArgsConstructor
public class BucketComponent {

  private final BucketConf bucketConf;

  @SneakyThrows
  public FileHash upload(File file, String bucketKey) {
    var s3Client = bucketConf.getS3Client();
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucketConf.getBucketName())
            .key(bucketKey)
            .build(),
        RequestBody.fromFile(file));
    return new FileHash(FileHashAlgorithm.NONE, null);
  }

  @SneakyThrows
  public File download(String bucketKey) {
    var destination =
        createTempFile(prefixFromBucketKey(bucketKey), suffixFromBucketKey(bucketKey));
    GetObjectRequest request =
        GetObjectRequest.builder()
            .bucket(bucketConf.getBucketName())
            .key(bucketKey)
            .build();
    try (var in = bucketConf.getS3Client().getObject(request)) {
      Files.write(destination.toPath(), in.readAllBytes());
    }
    return destination;
  }

  private String prefixFromBucketKey(String bucketKey) {
    return lastNameSplitByDot(bucketKey)[0];
  }

  private String suffixFromBucketKey(String bucketKey) {
    var splitByDot = lastNameSplitByDot(bucketKey);
    return splitByDot.length == 1 ? "" : splitByDot[splitByDot.length - 1];
  }

  private String[] lastNameSplitByDot(String bucketKey) {
    var splitByDash = bucketKey.split("/");
    var lastName = splitByDash[splitByDash.length - 1];
    return lastName.split("\\.");
  }

  public URL presign(String bucketKey, Duration expiration) {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder().bucket(bucketConf.getBucketName()).key(bucketKey).build();
    PresignedGetObjectRequest presignedRequest =
        bucketConf
            .getS3Presigner()
            .presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build());
    return presignedRequest.url();
  }

  public String getBucketName() {
    return bucketConf.getBucketName();
  }
}
