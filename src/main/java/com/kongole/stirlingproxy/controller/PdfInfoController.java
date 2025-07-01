package com.kongole.stirlingproxy.controller;

import com.kongole.stirlingproxy.dto.BookmarkInfo; // Make sure this DTO is accessible
import com.kongole.stirlingproxy.util.MultipartInputStreamFileResource; // Make sure this utility is accessible

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/get/pdf-info") // This is the base path for PDF info operations
public class PdfInfoController {

    @PostMapping(value = "/extract-bookmarks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BookmarkInfo>> getPdfBookmarks(
            @RequestParam("pdfFile") MultipartFile file) {

        List<BookmarkInfo> bookmarks = new ArrayList<>();
        PDDocument document = null;

        try {
            document = PDDocument.load(file.getInputStream());
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();

            if (outline != null) {
                processOutline(outline.getFirstChild(), bookmarks, document);
            }

            return ResponseEntity.ok(bookmarks);

        } catch (IOException e) {
            System.err.println("Error reading PDF file or during PDFBox processing: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
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

        int pageNumber = -1;

        if (item.getDestination() instanceof PDPageDestination) {
            PDPageDestination pageDest = (PDPageDestination) item.getDestination();
            if (pageDest.getPage() != null) {
                pageNumber = document.getPages().indexOf(pageDest.getPage()) + 1;
            } else if (pageDest.getPageNumber() >= 0) {
                pageNumber = pageDest.getPageNumber() + 1;
            }
        } else if (item.getDestination() instanceof PDNamedDestination) {
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

        if (pageNumber != -1) {
            bookmarks.add(new BookmarkInfo(item.getTitle(), pageNumber));
        } else {
            System.err.println("Could not resolve page number for bookmark: " + item.getTitle());
        }

        PDOutlineItem child = item.getFirstChild();
        while (child != null) {
            processOutline(child, bookmarks, document);
            child = child.getNextSibling();
        }
    }
}
