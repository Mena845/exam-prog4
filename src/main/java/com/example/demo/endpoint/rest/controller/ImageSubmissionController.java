package com.example.demo.endpoint.rest.controller;

import com.example.demo.endpoint.rest.dto.ImageSubmissionResponse;
import com.example.demo.service.ImageSubmissionService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/images")
@AllArgsConstructor
@Validated
public class ImageSubmissionController {

  private final ImageSubmissionService service;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ImageSubmissionResponse submit(
      @RequestParam @NotBlank @Email String email, @RequestParam MultipartFile image) {
    return service.submit(email, image);
  }

  @GetMapping
  public List<ImageSubmissionResponse> findAll() {
    return service.findAll();
  }
}
