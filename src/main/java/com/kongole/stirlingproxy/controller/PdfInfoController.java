package com.kongole.stirlingproxy.controller;

import com.kongole.stirlingproxy.dto.BookmarkInfo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/get/pdf-info")
public class PdfInfoController {

    private static final Logger logger = LoggerFactory.getLogger(PdfInfoController.class);

    @PostMapping(value = "/extract-bookmarks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BookmarkInfo>> extractBookmarks(@RequestParam("pdfFile") MultipartFile pdfFile) {
        if (pdfFile == null || pdfFile.isEmpty()) {
            logger.warn("Received a request for bookmark extraction with an empty or null PDF file.");
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }

        List<BookmarkInfo> bookmarks = new ArrayList<>();
        PDDocument document = null;
        try {
            document = PDDocument.load(pdfFile.getInputStream());
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();

            if (outline == null) {
                logger.info("PDF file '{}' has no document outline (bookmarks).", pdfFile.getOriginalFilename());
                return ResponseEntity.ok(Collections.emptyList());
            }

            processOutline(outline.getFirstChild(), bookmarks, 0, document);
            logger.info("Successfully extracted {} bookmarks from '{}'.", bookmarks.size(), pdfFile.getOriginalFilename());
            return ResponseEntity.ok(bookmarks);

        } catch (IOException e) {
            logger.error("IOException occurred while processing PDF file '{}': {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Collections.emptyList());
        } catch (Exception e) {
            logger.error("An unexpected error occurred during bookmark extraction from PDF file '{}': {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Collections.emptyList());
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    logger.error("Error closing PDF document '{}': {}", pdfFile.getOriginalFilename(), e.getMessage(), e);
                }
            }
        }
    }

    private void processOutline(PDOutlineItem bookmark, List<BookmarkInfo> bookmarks, int level, PDDocument document) throws IOException {
        while (bookmark != null) {
            int pageNumber = -1; // Default to -1 if page cannot be determined

            String bookmarkTitle = bookmark.getTitle();
            if (bookmarkTitle == null || bookmarkTitle.trim().isEmpty()) {
                bookmarkTitle = "[Untitled Bookmark]";
                logger.warn("Found an untitled bookmark at outline level {}.", level);
            }

            logger.debug("Processing bookmark: '{}' at level {}", bookmarkTitle, level);

            // Determine the page number based on destination or action
            if (bookmark.getDestination() != null) {
                logger.debug("Bookmark '{}' has a Destination of type: {}", bookmarkTitle, bookmark.getDestination().getClass().getName());
                PDPageDestination pageDest = null;

                if (bookmark.getDestination() instanceof PDPageDestination) {
                    pageDest = (PDPageDestination) bookmark.getDestination();
                } else if (bookmark.getDestination() instanceof PDNamedDestination) {
                    PDNamedDestination namedDest = (PDNamedDestination) bookmark.getDestination();
                    logger.debug("  -> PDNamedDestination found: '{}'", namedDest.getNamedDestination());
                    pageDest = document.getDocumentCatalog().findNamedDestinationPage(namedDest);
                    if (pageDest == null) {
                        logger.warn("Named destination '{}' could not be resolved to a valid page destination for bookmark '{}'. Page number will be -1.", namedDest.getNamedDestination(), bookmarkTitle);
                    }
                } else {
                    logger.warn("Unsupported destination type for bookmark '{}' at level {}: {}", bookmarkTitle, level, bookmark.getDestination().getClass().getName());
                }

                if (pageDest != null) {
                    // Always try to get the actual PDPage object and then its index
                    PDPage actualPage = pageDest.getPage();
                    if (actualPage != null) {
                        pageNumber = document.getPages().indexOf(actualPage) + 1; // PDFBox is 0-indexed
                        logger.debug("  -> Resolved via PDPage. Raw page index: {}, Calculated page number: {}", document.getPages().indexOf(actualPage), pageNumber);
                    } else {
                        // Fallback to getPageNumber() if getPage() returns null (though less reliable for this case)
                        int rawPageNum = pageDest.getPageNumber();
                        if (rawPageNum != -1) {
                             pageNumber = rawPageNum + 1;
                             logger.debug("  -> Resolved via getPageNumber(). Raw page number: {}, Calculated page number: {}", rawPageNum, pageNumber);
                        } else {
                            logger.warn("PDPageDestination for bookmark '{}' at level {} returned raw page number -1 and null PDPage.", bookmarkTitle, level);
                        }
                    }
                }
            } else if (bookmark.getAction() instanceof PDActionGoTo) {
                PDActionGoTo goToAction = (PDActionGoTo) bookmark.getAction();
                logger.debug("Bookmark '{}' has a GoTo Action.", bookmarkTitle);
                if (goToAction.getDestination() != null) {
                    logger.debug("  -> GoTo Action has a Destination of type: {}", goToAction.getDestination().getClass().getName());
                    PDPageDestination pageDest = null;
                    if (goToAction.getDestination() instanceof PDPageDestination) {
                        pageDest = (PDPageDestination) goToAction.getDestination();
                    } else if (goToAction.getDestination() instanceof PDNamedDestination) {
                        PDNamedDestination namedDest = (PDNamedDestination) goToAction.getDestination();
                        logger.debug("    -> PDNamedDestination found: '{}'", namedDest.getNamedDestination());
                        pageDest = document.getDocumentCatalog().findNamedDestinationPage(namedDest);
                        if (pageDest == null) {
                            logger.warn("Named destination '{}' from GoTo action could not be resolved to a valid page destination for bookmark '{}'. Page number will be -1.", namedDest.getNamedDestination(), bookmarkTitle);
                        }
                    } else {
                        logger.warn("Unsupported destination type within GoTo action for bookmark '{}' at level {}: {}", bookmarkTitle, level, goToAction.getDestination().getClass().getName());
                    }

                    if (pageDest != null) {
                        PDPage actualPage = pageDest.getPage();
                        if (actualPage != null) {
                            pageNumber = document.getPages().indexOf(actualPage) + 1;
                            logger.debug("    -> Resolved via PDPage. Raw page index: {}, Calculated page number: {}", document.getPages().indexOf(actualPage), pageNumber);
                        } else {
                            int rawPageNum = pageDest.getPageNumber();
                            if (rawPageNum != -1) {
                                pageNumber = rawPageNum + 1;
                                logger.debug("    -> Resolved via getPageNumber(). Raw page number: {}, Calculated page number: {}", rawPageNum, pageNumber);
                            } else {
                                logger.warn("PDPageDestination from GoTo action for bookmark '{}' at level {} returned raw page number -1 and null PDPage.", bookmarkTitle, level);
                            }
                        }
                    }
                } else {
                    logger.warn("GoTo action for bookmark '{}' at level {} has no discernable destination.", bookmarkTitle, level);
                }
            } else {
                logger.warn("Unsupported destination or action type for bookmark '{}' at level {}: {}", bookmarkTitle, level,
                            (bookmark.getDestination() != null ? bookmark.getDestination().getClass().getName() :
                             (bookmark.getAction() != null ? bookmark.getAction().getClass().getName() : "null")));
            }

            bookmarks.add(new BookmarkInfo(bookmarkTitle, pageNumber, level));
            processOutline(bookmark.getFirstChild(), bookmarks, level + 1, document);
            bookmark = bookmark.getNextSibling();
        }
    }
}