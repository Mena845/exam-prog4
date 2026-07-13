package com.example.demo.file.bucket;

import com.example.demo.PojaGenerated;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@PojaGenerated
@Configuration
public class BucketConf {

  @Getter private final String bucketName;
  @Getter private final S3Presigner s3Presigner;
  @Getter private final S3Client s3Client;

  public BucketConf(
      @Value("eu-west-3") String regionString, @Value("${aws.s3.bucket}") String bucketName) {
    this.bucketName = bucketName;
    var region = Region.of(regionString);
    this.s3Presigner = S3Presigner.builder().region(region).build();
    this.s3Client = S3Client.builder().region(region).build();
  }
}
