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
@RequestMapping("/api/stirling") // Changed base path for clarity, e.g., /api/stirling/security/add-password
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
     *
     * Endpoints it can handle:
     * - /api/v1/security/remove-cert-sign
     * - /api/v1/security/get-info-on-pdf (returns JSON, but handled as byte[] for flexibility)
     * - /api/v1/misc/unlock-pdf-forms
     * - /api/v1/misc/show-javascript
     * - /api/v1/misc/repair
     * - /api/v1/misc/decompress-pdf
     * - /api/v1/general/remove-image-pdf
     * - /api/v1/general/pdf-to-single-page
     * - /api/v1/convert/pdf/xml (returns XML, handled as byte[])
     * - /api/v1/convert/pdf/markdown (returns Markdown, handled as byte[])
     * - /api/v1/convert/pdf/html (returns HTML, handled as byte[])
     * - /api/v1/analysis/security-info (returns JSON, handled as byte[])
     */
    @PostMapping(value = "/{category}/{action}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> proxySingleFileUpload(
            @PathVariable String category,
            @PathVariable String action,
            @RequestParam("file") MultipartFile file) {

        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + category + "/" + action;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // Accept any content type for the response, as it could be PDF, JSON, XML, etc.
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
     * Proxy for endpoints that take a file and additional parameters in multipart/form-data.
     * This is a more generalized version that can be used for many endpoints.
     *
     * Example usage (conceptual):
     * POST /api/stirling/security/add-password
     * - file: <PDF file>
     * - password: "yourpassword"
     * - permissions: "Print,Copy"
     * - newPermissions: true
     * - encrypt: true
     * - keyLength: 128
     *
     * @param category The API category (e.g., "security", "misc", "general").
     * @param action The specific action (e.g., "add-password", "add-watermark").
     * @param file The PDF file to upload.
     * @param allRequestParams All other request parameters as a map. This allows flexibility.
     */
    @PostMapping(value = "/{category}/{action}/with-params", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> proxyFileUploadWithParams(
            @PathVariable String category,
            @PathVariable String action,
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> allRequestParams) { // Catch all other form fields

        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + category + "/" + action;
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

        // Add all other parameters from the request to the multipart body
        allRequestParams.forEach((key, value) -> {
            if (!key.equals("file")) { // 'file' is handled separately
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
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error: " + e.getMessage()).getBytes());
        }
    }


    /**
     * Specific proxy for /api/v1/convert/url/pdf as it takes a URL instead of a file upload,
     * though it's still multipart/form-data.
     */
    @PostMapping(value = "/convert/url/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("url", url);
        if (footer != null) body.add("footer", footer);
        if (header != null) body.add("header", header);
        if (landscape != null) body.add("landscape", landscape.toString());
        if (omitBackground != null) body.add("omitBackground", omitBackground.toString());
        if (scale != null) body.add("scale", scale.toString());
        if (printMediaType != null) body.add("printMediaType", printMediaType.toString());
        if (nativePageRanges != null) body.add("nativePageRanges", nativePageRanges);


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
     * Specific proxy for /api/v1/misc/extract-images as it returns a ZIP file.
     * The response content type is application/zip.
     */
    @PostMapping(value = "/misc/extract-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> extractImagesProxy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "imageFormat", required = false) String imageFormat) {

        String targetUrl = STIRLING_PDF_URL + "/api/v1/misc/extract-images";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM)); // Expect ZIP or image bytes

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file: " + e.getMessage()).getBytes());
        }
        if (imageFormat != null) {
            body.add("imageFormat", imageFormat);
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
     * Specific proxy for /api/v1/misc/extract-image-scans as it returns a ZIP file.
     * The response content type is application/zip.
     */
    @PostMapping(value = "/misc/extract-image-scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> extractImageScansProxy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "angleThreshold", required = false) Double angleThreshold,
            @RequestParam(value = "tolerance", required = false) Double tolerance,
            @RequestParam(value = "minArea", required = false) Double minArea,
            @RequestParam(value = "minContourArea", required = false) Double minContourArea,
            @RequestParam(value = "borderSize", required = false) Integer borderSize) {

        String targetUrl = STIRLING_PDF_URL + "/api/v1/misc/extract-image-scans";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file: " + e.getMessage()).getBytes());
        }
        if (angleThreshold != null) body.add("angleThreshold", angleThreshold.toString());
        if (tolerance != null) body.add("tolerance", tolerance.toString());
        if (minArea != null) body.add("minArea", minArea.toString());
        if (minContourArea != null) body.add("minContourArea", minContourArea.toString());
        if (borderSize != null) body.add("borderSize", borderSize.toString());

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
     * Specific proxy for /api/v1/convert/img/pdf as it takes multiple image files.
     */
    @PostMapping(value = "/convert/img/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convertImagesToPdfProxy(
            @RequestParam("files") MultipartFile[] files, // Array for multiple files
            @RequestParam(value = "stretch", required = false) Boolean stretch,
            @RequestParam(value = "autoRotate", required = false) Boolean autoRotate) {

        String targetUrl = STIRLING_PDF_URL + "/api/v1/convert/img/pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            for (MultipartFile file : files) {
                body.add("files", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file(s): " + e.getMessage()).getBytes());
        }

        if (stretch != null) body.add("stretch", stretch.toString());
        if (autoRotate != null) body.add("autoRotate", autoRotate.toString());

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
     * Specific proxy for /api/v1/general/merge-pdfs as it takes multiple PDF files.
     */
    @PostMapping(value = "/general/merge-pdfs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> mergePdfsProxy(
            @RequestParam("files") MultipartFile[] files) { // Array for multiple files

        String targetUrl = STIRLING_PDF_URL + "/api/v1/general/merge-pdfs";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            for (MultipartFile file : files) {
                body.add("files", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file(s): " + e.getMessage()).getBytes());
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
     * Specific proxy for /api/v1/general/overlay-pdfs as it takes multiple PDF files and a mode.
     */
    @PostMapping(value = "/general/overlay-pdfs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> overlayPdfsProxy(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("mode") String mode) {

        String targetUrl = STIRLING_PDF_URL + "/api/v1/general/overlay-pdfs";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            for (MultipartFile file : files) {
                body.add("files", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file(s): " + e.getMessage()).getBytes());
        }
        body.add("mode", mode);

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

    // You can remove or modify the original proxyPostJson as most Stirling PDF
    // operations are multipart file uploads, not JSON request bodies.
    // However, if you anticipate future API changes or other services requiring it,
    // you might keep a modified version.
    /*
    @PostMapping(value = "/{category}/{action}/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> proxyPostJson(
            @PathVariable String category,
            @PathVariable String action,
            @RequestBody String requestBody) { // Assuming requestBody is JSON

        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + category + "/" + action;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.ALL)); // Accept all for response

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, entity, byte[].class);
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
    */

    // --- Utility Class (assuming you have this or need to create it) ---
    // You should ensure MultipartInputStreamFileResource is in your util package.
    // Example implementation:
    /*
    package com.kongole.stirlingproxy.util;

    import org.springframework.core.io.InputStreamResource;

    import java.io.InputStream;

    public class MultipartInputStreamFileResource extends InputStreamResource {

        private final String filename;

        public MultipartInputStreamFileResource(InputStream inputStream, String filename) {
            super(inputStream);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }

        @Override
        public long contentLength() {
            // We don't know the content length, so return -1.
            // Spring will handle streaming it.
            return -1;
        }
    }
    */
}
