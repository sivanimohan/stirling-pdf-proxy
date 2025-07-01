package com.kongole.stirlingproxy.controller;

import com.kongole.stirlingproxy.util.MultipartInputStreamFileResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;

@RestController
@RequestMapping("/get")
public class StirlingPdfFullProxyController {

    // Dynamically loaded from env via application.properties: stirling.base.url=${STIRLING_BASE_URL}
    private final String STIRLING_PDF_URL = System.getenv().getOrDefault("STIRLING_BASE_URL",
            "https://stirling-pdf-railway-poetic-courtesy.up.railway.app");

    private final RestTemplate restTemplate = new RestTemplate();

    // Root proxy test endpoint
    @GetMapping("/")
    public String status() {
        return "✅ Stirling PDF Proxy is running!";
    }

    // JSON POST proxy (e.g. /get/convert/pdf/text)
    @PostMapping("/{category}/{action}")
    public ResponseEntity<String> proxyPostJson(
            @PathVariable String category,
            @PathVariable String action,
            @RequestBody String requestBody
    ) {
        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + category + "/" + action;

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

    // File upload POST proxy (e.g. /get/convert/pdf/word/upload)
    @PostMapping("/{category}/{action}/upload")
    public ResponseEntity<String> proxyFileUpload(
            @PathVariable String category,
            @PathVariable String action,
            @RequestParam("file") MultipartFile file
    ) {
        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + category + "/" + action;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, requestEntity, String.class);

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
