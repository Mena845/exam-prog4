package com.my.company.endpoint.rest.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class ImageSubmissionResponse {
  UUID id;
  String filename;
  String email;
  OffsetDateTime createdAt;
  String status;
  String bwImageUrl;
}
