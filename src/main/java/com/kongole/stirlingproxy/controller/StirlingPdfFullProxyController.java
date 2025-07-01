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

    // You can replace this with @Value("${stirling.base.url}") if using application.properties/env
    private static final String STIRLING_PDF_URL = System.getenv("STIRLING_BASE_URL") != null
            ? System.getenv("STIRLING_BASE_URL")
            : "https://stirling-pdf-railway-poetic-courtesy.up.railway.app";

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/")
    public String status() {
        return "✅ Stirling PDF Proxy is running!";
    }

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

    @PostMapping("/general/split-pdf-by-chapters/upload")
    public ResponseEntity<String> splitByChaptersProxy(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bookmarkLevel") Integer level
    ) {
        String targetUrl = STIRLING_PDF_URL + "/api/v1/general/split-pdf-by-chapters";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            body.add("bookmarkLevel", level.toString());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, requestEntity, String.class);

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
