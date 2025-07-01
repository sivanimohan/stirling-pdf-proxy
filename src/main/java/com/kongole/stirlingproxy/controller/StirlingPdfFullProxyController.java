package com.kongole.stirlingproxy.controller;

import com.kongole.stirlingproxy.util.MultipartInputStreamFileResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;

@RestController
@RequestMapping("/get")
public class StirlingPdfFullProxyController {

    @Value("${stirling.base.url}")
    private String stirlingBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/")
    public String status() {
        return "✅ Stirling PDF Proxy is running!";
    }

    // Proxy JSON POST to API
    @PostMapping("/{category}/{action}")
    public ResponseEntity<String> proxyPostJson(
            @PathVariable String category,
            @PathVariable String action,
            @RequestBody String requestBody
    ) {
        String targetUrl = stirlingBaseUrl + "/api/v1/" + category + "/" + action;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, entity, String.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    // Proxy multipart file upload with optional form fields
    @PostMapping("/{category}/{action}/upload")
    public ResponseEntity<String> proxyFileUploadWithParams(
            @PathVariable String category,
            @PathVariable String action,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) MultiValueMap<String, String> allParams
    ) {
        String targetUrl = stirlingBaseUrl + "/api/v1/" + category + "/" + action;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));

            if (allParams != null) {
                for (String key : allParams.keySet()) {
                    for (String value : allParams.get(key)) {
                        body.add(key, value);
                    }
                }
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, requestEntity, String.class);

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
