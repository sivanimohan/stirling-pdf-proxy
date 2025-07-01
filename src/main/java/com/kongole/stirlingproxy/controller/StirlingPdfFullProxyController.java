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

    // You need to ensure proxySingleFileUpload also has a guaranteed return path.
    // Apply the same try-catch logic as below if it's currently causing issues.
    @PostMapping(value = "/{category}/{action}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> proxySingleFileUpload(
            @PathVariable String category,
            @PathVariable String action,
            @RequestParam("file") MultipartFile file) {

        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + category + "/" + action;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.ALL));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            // **CHANGED: "pdfFile" to "fileInput"**
            body.add("fileInput", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);

            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (IOException e) { // Catch IOException specifically for file processing
            System.err.println("Error reading file in proxySingleFileUpload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxySingleFileUpload: " + e.getMessage()).getBytes());
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

        try { // <<< --- Wrapped the entire logic in a single try-catch
            String targetUrl = STIRLING_PDF_URL + "/api/v1/" + stirlingCategory + "/" + stirlingAction;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.ALL));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            // **CHANGED: "pdfFile" to "fileInput"**
            body.add("fileInput", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            // If IOException occurs here, it will be caught by the outer catch block

            allRequestParams.forEach((key, value) -> {
                // **ADJUSTED: Now excludes "file" (your proxy's param) and "fileInput" (Stirling's param)**
                if (!key.equals("file") && !key.equals("fileInput")) {
                    body.add(key, value);
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);

            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());

        } catch (IOException e) { // Specific catch for file reading issues
            System.err.println("Error reading file or network issue during proxyFileUploadWithParams: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file or network issue: " + e.getMessage()).getBytes());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // Log the error for debugging on your server
            System.err.println("Error calling Stirling PDF API: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            e.printStackTrace(); // Print full stack trace for unexpected errors
            System.err.println("Unexpected internal server error in proxyFileUploadWithParams: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }


    // Make sure to apply similar robust try-catch blocks to ALL other proxy methods
    // (urlToPdfProxy, extractImagesProxy, etc.) to ensure a return statement is always reached.

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

        try {
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

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API (urlToPdfProxy): " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected internal server error in urlToPdfProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }


    @PostMapping(value = "/api/stirling/misc/extract-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> extractImagesProxy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "imageFormat", required = false) String imageFormat) {
        try {
            String targetUrl = STIRLING_PDF_URL + "/api/v1/misc/extract-images";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            // **CHANGED: "pdfFile" to "fileInput"**
            body.add("fileInput", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            if (imageFormat != null) {
                body.add("imageFormat", imageFormat);
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (IOException e) {
            System.err.println("Error reading file or network issue during extractImagesProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file or network issue: " + e.getMessage()).getBytes());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API (extractImagesProxy): " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected internal server error in extractImagesProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }


    @PostMapping(value = "/api/stirling/misc/extract-image-scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> extractImageScansProxy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "angleThreshold", required = false) Double angleThreshold,
            @RequestParam(value = "tolerance", required = false) Double tolerance,
            @RequestParam(value = "minArea", required = false) Double minArea,
            @RequestParam(value = "minContourArea", required = false) Double minContourArea,
            @RequestParam(value = "borderSize", required = false) Integer borderSize) {
        try {
            String targetUrl = STIRLING_PDF_URL + "/api/v1/misc/extract-image-scans";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            // **CHANGED: "pdfFile" to "fileInput"**
            body.add("fileInput", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            if (angleThreshold != null) body.add("angleThreshold", angleThreshold.toString());
            if (tolerance != null) body.add("tolerance", tolerance.toString());
            if (minArea != null) body.add("minArea", minArea.toString());
            if (minContourArea != null) body.add("minContourArea", minContourArea.toString());
            if (borderSize != null) body.add("borderSize", borderSize.toString());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (IOException e) {
            System.err.println("Error reading file or network issue during extractImageScansProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file or network issue: " + e.getMessage()).getBytes());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API (extractImageScansProxy): " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected internal server error in extractImageScansProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }


    @PostMapping(value = "/api/stirling/convert/img/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convertImagesToPdfProxy(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "stretch", required = false) Boolean stretch,
            @RequestParam(value = "autoRotate", required = false) Boolean autoRotate) {
        try {
            String targetUrl = STIRLING_PDF_URL + "/api/v1/convert/img/pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (MultipartFile file : files) {
                body.add("files", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            }

            if (stretch != null) body.add("stretch", stretch.toString());
            if (autoRotate != null) body.add("autoRotate", autoRotate.toString());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (IOException e) {
            System.err.println("Error reading file(s) or network issue during convertImagesToPdfProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file(s) or network issue: " + e.getMessage()).getBytes());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API (convertImagesToPdfProxy): " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected internal server error in convertImagesToPdfProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }


    @PostMapping(value = "/api/stirling/general/merge-pdfs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> mergePdfsProxy(
            @RequestParam("files") MultipartFile[] files) {
        try {
            String targetUrl = STIRLING_PDF_URL + "/api/v1/general/merge-pdfs";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (MultipartFile file : files) {
                body.add("files", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (IOException e) {
            System.err.println("Error reading file(s) or network issue during mergePdfsProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file(s) or network issue: " + e.getMessage()).getBytes());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API (mergePdfsProxy): " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected internal server error in mergePdfsProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }


    @PostMapping(value = "/api/stirling/general/overlay-pdfs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> overlayPdfsProxy(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("mode") String mode) {
        try {
            String targetUrl = STIRLING_PDF_URL + "/api/v1/general/overlay-pdfs";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (MultipartFile file : files) {
                body.add("files", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            }
            body.add("mode", mode);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (IOException e) {
            System.err.println("Error reading file(s) or network issue during overlayPdfsProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file(s) or network issue: " + e.getMessage()).getBytes());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API (overlayPdfsProxy): " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected internal server error in overlayPdfsProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }
}
