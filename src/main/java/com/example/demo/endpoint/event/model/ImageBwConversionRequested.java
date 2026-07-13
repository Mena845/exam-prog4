package com.example.demo.endpoint.event.model;

import java.time.Duration;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@EqualsAndHashCode(callSuper = false)
@ToString
public class ImageBwConversionRequested extends PojaEvent {
    private UUID imageId;
    private String originalS3Key;

    @Override
    public Duration maxConsumerDuration() {
        return Duration.ofSeconds(30);
    }

    @Override
    public Duration maxConsumerBackoffBetweenRetries() {
        return Duration.ofSeconds(15);
    }
}