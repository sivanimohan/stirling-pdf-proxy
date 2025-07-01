package com.kongole.stirlingproxy.controller;

import com.kongole.stirlingproxy.util.MultipartInputStreamFileResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/get") // This is your proxy's base URL
public class StirlingPdfFullProxyController {

    private static final String STIRLING_PDF_URL = System.getenv("STIRLING_BASE_URL") != null
            ? System.getenv("STIRLING_BASE_URL")
            : "https://stirling-pdf-railway-poetic-courtesy.up.railway.app";

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/")
    public String status() {
        return "✅ Stirling PDF Proxy is running!";
    }

    /**
     * Generic proxy for POST requests that primarily involve a single file upload
     * without additional form parameters, returning a PDF or other binary data.
     * Maps to /get/{category}/{action}
     */
    @PostMapping(value = "/{category}/{action}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> proxySingleFileUpload(
            @PathVariable String category,
            @PathVariable String action,
            @RequestParam("file") MultipartFile file) {

        // The target URL needs to correctly map to the Stirling PDF API's structure
        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + category + "/" + action;
        // ... (rest of the method code as before)
        // This method's logic is fine if category and action are "security" and "remove-cert-sign" respectively.
        // But for your curl command /filter/filter-page-size/with-params, this isn't the right method.
        // It would be invoked if you did `curl .../get/filter/filter-page-size`
        // But your curl is `.../get/api/stirling/filter/filter-page-size/with-params`
        // So let's re-think the path matching.
        // The /api/stirling segment in your curl command needs to be properly consumed by Spring path variables.

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.ALL));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file: " + e.getMessage()).getBytes());
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error: " + e.getMessage()).getBytes());
        }
    }


    /**
     * Corrected Path Variable Handling for your specific curl command structure
     * Your curl is: /get/api/stirling/filter/filter-page-size/with-params
     * This means the proxy path starts with /api/stirling and then the Stirling categories/actions
     */
    @PostMapping(value = "/api/stirling/{stirlingCategory}/{stirlingAction}/with-params", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> proxyFileUploadWithParams(
            @PathVariable String stirlingCategory,
            @PathVariable String stirlingAction,
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> allRequestParams) {

        // Construct the target URL for the actual Stirling PDF API
        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + stirlingCategory + "/" + stirlingAction;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.ALL));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file: " + e.getMessage()).getBytes());
        }

        allRequestParams.forEach((key, value) -> {
            if (!key.equals("file")) {
                body.add(key, value);
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // Log the error for debugging on your server
            System.err.println("Error calling Stirling PDF API: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            // Log the error for debugging on your server
            e.printStackTrace(); // Print full stack trace for unexpected errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }


    // Re-check and adjust other specific proxy methods similarly
    // For example, for `urlToPdfProxy`:
    @PostMapping(value = "/api/stirling/convert/url/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> urlToPdfProxy(
            @RequestParam("url") String url,
            @RequestParam(value = "footer", required = false) String footer,
            @RequestParam(value = "header", required = false) String header,
            @RequestParam(value = "landscape", required = false) Boolean landscape,
            @RequestParam(value = "omitBackground", required = false) Boolean omitBackground,
            @RequestParam(value = "scale", required = false) Double scale,
            @RequestParam(value = "printMediaType", required = false) Boolean printMediaType,
            @RequestParam(value = "nativePageRanges", required = false) String nativePageRanges) {

        String targetUrl = STIRLING_PDF_URL + "/api/v1/convert/url/pdf";
        // ... (rest of the method code)
    }

    // Adjust other methods like extractImagesProxy, extractImageScansProxy, convertImagesToPdfProxy, mergePdfsProxy, overlayPdfsProxy
    // to start their @PostMapping path with "/api/stirling/" as well,
    // and correctly map their path variables to the Stirling API's categories/actions.

    // ... (rest of the controller, including the utility class at the bottom)
}
