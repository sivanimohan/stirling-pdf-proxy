package com.kongole.stirlingproxy.controller;

import com.kongole.stirlingproxy.util.MultipartInputStreamFileResource;
import com.kongole.stirlingproxy.dto.BookmarkInfo; // Import your new DTO

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// PDFBox Imports
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
// Potentially other destination types if you encounter them often:
// import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;


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

    @PostMapping(value = "/{category}/{action}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> proxySingleFileUpload(
            @PathVariable String category,
            @PathVariable String action,
            @RequestParam("fileInput") MultipartFile file) {

        String targetUrl = STIRLING_PDF_URL + "/api/v1/" + category + "/" + action;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.ALL));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
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
        } catch (IOException e) {
            System.err.println("Error reading file in proxySingleFileUpload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxySingleFileUpload: " + e.getMessage()).getBytes());
        }
    }


    @PostMapping(value = "/api/stirling/{stirlingCategory}/{stirlingAction}/with-params", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> proxyFileUploadWithParams(
            @PathVariable String stirlingCategory,
            @PathVariable String stirlingAction,
            @RequestParam("fileInput") MultipartFile file,
            @RequestParam Map<String, String> allRequestParams) {

        try {
            String targetUrl = STIRLING_PDF_URL + "/api/v1/" + stirlingCategory + "/" + stirlingAction;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.ALL));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>(); // Corrected from LinkedMultiMultiValueMap
            body.add("fileInput", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));

            allRequestParams.forEach((key, value) -> {
                if (!key.equals("fileInput")) {
                    body.add(key, value);
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);

            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());

        } catch (IOException e) {
            System.err.println("Error reading file or network issue during proxyFileUploadWithParams: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file or network issue: " + e.getMessage()).getBytes());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected internal server error in proxyFileUploadWithParams: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }


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
            @RequestParam("fileInput") MultipartFile file,
            @RequestParam(value = "imageFormat", required = false) String imageFormat) {
        try {
            String targetUrl = STIRLING_PDF_URL + "/api/v1/misc/extract-images";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
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
            @RequestParam("fileInput") MultipartFile file,
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

    @PostMapping(value = "/api/stirling/general/split-pdf-by-chapters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> splitPdfByChaptersProxy(
            @RequestParam("fileInput") MultipartFile file,
            @RequestParam("includeMetadata") Boolean includeMetadata,
            @RequestParam("allowDuplicates") Boolean allowDuplicates,
            @RequestParam("bookmarkLevel") Integer bookmarkLevel) {
        try {
            String targetUrl = STIRLING_PDF_URL + "/api/v1/general/split-pdf-by-chapters";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("fileInput", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
            body.add("includeMetadata", includeMetadata.toString());
            body.add("allowDuplicates", allowDuplicates.toString());
            body.add("bookmarkLevel", bookmarkLevel.toString());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(targetUrl, requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (IOException e) {
            System.err.println("Error reading file or network issue during splitPdfByChaptersProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error reading file or network issue: " + e.getMessage()).getBytes());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error calling Stirling PDF API (splitPdfByChaptersProxy): " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unexpected internal server error in splitPdfByChaptersProxy: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Internal server error in proxy: " + e.getMessage()).getBytes());
        }
    }

    // --- NEW ENDPOINT FOR PDF BOOKMARK EXTRACTION ---
    @PostMapping(value = "/pdf-info/extract-bookmarks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BookmarkInfo>> getPdfBookmarks(
            @RequestParam("pdfFile") MultipartFile file) { // Using "pdfFile" as the parameter name

        List<BookmarkInfo> bookmarks = new ArrayList<>();
        PDDocument document = null; // Initialize to null for finally block

        try {
            document = PDDocument.load(file.getInputStream());
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();

            if (outline != null) {
                // Recursively process the outline items
                processOutline(outline.getFirstChild(), bookmarks, document);
            }

            return ResponseEntity.ok(bookmarks);

        } catch (IOException e) {
            System.err.println("Error reading PDF file or during PDFBox processing: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // Or return an error DTO
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            e.printStackTrace();
            System.err.println("Unexpected internal server error during PDF bookmark extraction: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    System.err.println("Error closing PDF document: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Helper method to recursively process PDOutlineItem and extract bookmark information.
     * Converts 0-based PDFBox page index to 1-based page number.
     * Handles different destination types to find the target page.
     */
    private void processOutline(PDOutlineItem item, List<BookmarkInfo> bookmarks, PDDocument document) throws IOException {
        if (item == null) {
            return;
        }

        int pageNumber = -1; // Default to -1 if page cannot be determined

        // Determine the target page number
        if (item.getDestination() instanceof PDPageDestination) {
            PDPageDestination pageDest = (PDPageDestination) item.getDestination();
            if (pageDest.getPage() != null) {
                // Get 0-based page index from the PDPage object, then convert to 1-based
                pageNumber = document.getPages().indexOf(pageDest.getPage()) + 1;
            } else if (pageDest.getPageNumber() >= 0) {
                // Fallback: If page object is null, but a 0-based page number is provided
                pageNumber = pageDest.getPageNumber() + 1;
            }
        } else if (item.getDestination() instanceof PDNamedDestination) {
            // Named destinations need to be resolved to a PDPageDestination
            PDNamedDestination namedDest = (PDNamedDestination) item.getDestination();
            PDPageDestination resolvedDest = document.getDocumentCatalog().findNamedDestinationPage(namedDest);
            if (resolvedDest != null) {
                if (resolvedDest.getPage() != null) {
                    pageNumber = document.getPages().indexOf(resolvedDest.getPage()) + 1;
                } else if (resolvedDest.getPageNumber() >= 0) {
                    pageNumber = resolvedDest.getPageNumber() + 1;
                }
            }
        }
        // You can add more 'else if' blocks here for other PDDestination subtypes if needed
        // e.g., PDPageFitWidthDestination, PDPageXYZDestination if you encounter issues
        // For most common PDFs, PDPageDestination and PDNamedDestination cover the majority.


        // Add the bookmark if a valid page number was found
        if (pageNumber != -1) {
            bookmarks.add(new BookmarkInfo(item.getTitle(), pageNumber));
        } else {
            // Log if a bookmark destination couldn't be resolved, for debugging
            System.err.println("Could not resolve page number for bookmark: " + item.getTitle());
        }


        // Recursively process children (sub-bookmarks)
        PDOutlineItem child = item.getFirstChild();
        while (child != null) {
            processOutline(child, bookmarks, document);
            child = child.getNextSibling();
        }
    }
}
