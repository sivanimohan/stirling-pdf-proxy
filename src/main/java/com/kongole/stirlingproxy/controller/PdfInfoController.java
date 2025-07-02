package com.kongole.stirlingproxy.controller;

import com.kongole.stirlingproxy.dto.BookmarkInfo;
import org.apache.pdfbox.pdmodel.PDDocumentpackage com.kongole.stirlingproxy.controller;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/") // Changed to root to allow flexible sub-paths for different functionalities
public class PdfInfoController {

    private static final Logger logger = LoggerFactory.getLogger(PdfInfoController.class);

    /**
     * Extracts bookmarks (outline items) from a PDF file.
     * @param pdfFile The PDF file to process.
     * @return A list of BookmarkInfo objects.
     */
    @PostMapping(value = "/get/pdf-info/extract-bookmarks", produces = MediaType.APPLICATION_JSON_VALUE)
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

    /**
     * Splits a PDF into multiple smaller PDFs based on a specified bookmark level,
     * and returns them as a ZIP file.
     *
     * @param fileInput The PDF file to split.
     * @param includeMetadata Whether to include metadata (not implemented, placeholder).
     * @param allowDuplicates Whether to allow duplicate pages (not implemented, placeholder).
     * @param bookmarkLevel The outline level at which to split the PDF into chapters (1-indexed).
     * @return A ResponseEntity containing a ZIP file with the split PDF chapters.
     */
    @PostMapping(value = "/get/api/stirling/general/split-pdf-by-chapters", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> splitPdfByChapters(
            @RequestParam("fileInput") MultipartFile fileInput,
            @RequestParam(value = "includeMetadata", defaultValue = "false") boolean includeMetadata, // Placeholder for future use
            @RequestParam(value = "allowDuplicates", defaultValue = "false") boolean allowDuplicates, // Placeholder for future use
            @RequestParam(value = "bookmarkLevel", defaultValue = "1") int bookmarkLevel) {

        if (fileInput == null || fileInput.isEmpty()) {
            logger.warn("Received a request for PDF splitting with an empty or null PDF file.");
            return ResponseEntity.badRequest().body(new byte[0]); // Return empty byte array for bad request
        }
        if (bookmarkLevel < 1) {
            logger.warn("Invalid bookmarkLevel received: {}. Must be 1 or greater.", bookmarkLevel);
            return ResponseEntity.badRequest().body(new byte[0]);
        }

        PDDocument document = null;
        ByteArrayOutputStream zipBos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(zipBos)) {
            document = PDDocument.load(fileInput.getInputStream());
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();

            if (outline == null) {
                logger.info("PDF file '{}' has no document outline. Cannot split by chapters.", fileInput.getOriginalFilename());
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"chapters_no_outline.zip\"")
                        .body(new byte[0]);
            }

            List<BookmarkInfo> allBookmarks = new ArrayList<>();
            processOutline(outline.getFirstChild(), allBookmarks, 0, document);

            List<ChapterInfo> chapters = new ArrayList<>();
            // bookmarkLevel is 1-indexed for the user, but internal level in BookmarkInfo is 0-indexed
            for (BookmarkInfo bm : allBookmarks) {
                if (bm.getLevel() == (bookmarkLevel - 1) && bm.getPageNumber() != -1) {
                    // Convert to 0-indexed page for internal processing
                    chapters.add(new ChapterInfo(bm.getTitle(), bm.getPageNumber() - 1, bm.getLevel()));
                }
            }
            chapters.sort(Comparator.comparingInt(ChapterInfo::getStartPage));

            if (chapters.isEmpty()) {
                logger.info("No bookmarks found at level {} in PDF '{}'. Cannot split by chapters.", bookmarkLevel, fileInput.getOriginalFilename());
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"chapters_level_not_found.zip\"")
                        .body(new byte[0]);
            }

            int totalPages = document.getNumberOfPages();
            for (int i = 0; i < chapters.size(); i++) {
                ChapterInfo currentChapter = chapters.get(i);
                int startPageIndex = currentChapter.getStartPage();
                int endPageIndex;

                // Determine the end page for the current chapter
                if (i + 1 < chapters.size()) {
                    // End page is the page *before* the start of the next chapter
                    endPageIndex = chapters.get(i + 1).getStartPage() - 1;
                } else {
                    // If it's the last chapter, it goes to the end of the document
                    endPageIndex = totalPages - 1;
                }

                // Adjust endPageIndex to not exceed document bounds
                endPageIndex = Math.min(endPageIndex, totalPages - 1);

                if (startPageIndex > endPageIndex) {
                    logger.warn("Bookmark '{}' (level {}) points to a page ({}) that is after its calculated end page ({}). Skipping this chapter.",
                            currentChapter.getTitle(), currentChapter.getLevel() + 1, currentChapter.getStartPage() + 1, endPageIndex + 1);
                    continue; // Skip invalid chapter range
                }
                if (startPageIndex >= totalPages || startPageIndex < 0) {
                     logger.warn("Bookmark '{}' (level {}) points to an invalid start page index {}. Skipping this chapter.",
                             currentChapter.getTitle(), currentChapter.getLevel() + 1, startPageIndex);
                     continue;
                }

                PDDocument chapterDoc = null;
                try {
                    chapterDoc = new PDDocument();
                    for (int pageNum = startPageIndex; pageNum <= endPageIndex; pageNum++) {
                        if (pageNum < document.getNumberOfPages()) { // Safety check
                            chapterDoc.addPage(document.getPage(pageNum));
                        } else {
                            logger.warn("Attempted to add page {} which is out of bounds for document '{}' (total pages: {}). Stopping chapter extraction for '{}'.",
                                pageNum + 1, fileInput.getOriginalFilename(), document.getNumberOfPages(), currentChapter.getTitle());
                            break;
                        }
                    }

                    // Clean up title for filename (remove invalid chars)
                    String chapterTitle = currentChapter.getTitle().replaceAll("[^a-zA-Z0-9.\\-_ ]", "").trim();
                    if (chapterTitle.isEmpty()) {
                        chapterTitle = "Chapter_" + (i + 1);
                    }
                    String chapterFileName = String.format("%03d-%s.pdf", i + 1, chapterTitle);

                    ZipEntry zipEntry = new ZipEntry(chapterFileName);
                    zos.putNextEntry(zipEntry);

                    ByteArrayOutputStream chapterPdfBytes = new ByteArrayOutputStream();
                    chapterDoc.save(chapterPdfBytes); // Save PDF content to a temporary buffer

                    zos.write(chapterPdfBytes.toByteArray()); // Write buffer's content to the zip entry
                    zos.flush(); // <--- ADDED: Flush after writing bytes to the entry
                    zos.closeEntry();
                    logger.debug("Added chapter '{}' (pages {}-{}) to zip.", currentChapter.getTitle(), startPageIndex + 1, endPageIndex + 1);

                } finally {
                    if (chapterDoc != null) {
                        chapterDoc.close();
                    }
                }
            }

            // zos.close() is handled by try-with-resources.
            // An explicit flush here doesn't hurt, but the final close should write the central directory.
            zos.flush(); // <--- ADDED: Flush before the main ZipOutputStream closes

            logger.info("Successfully split PDF '{}' into {} chapters at level {}.", fileInput.getOriginalFilename(), chapters.size(), bookmarkLevel);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileInput.getOriginalFilename().replace(".pdf", "_chapters.zip") + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBos.toByteArray());

        } catch (IOException e) {
            logger.error("IOException occurred while splitting PDF file '{}': {}", fileInput.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new byte[0]);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during PDF splitting from '{}': {}", fileInput.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new byte[0]);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    logger.error("Error closing PDF document '{}' after splitting: {}", fileInput.getOriginalFilename(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Helper method to process PDF outline recursively and collect all bookmarks.
     * This method is used by both extractBookmarks and splitPdfByChapters.
     */
    private void processOutline(PDOutlineItem bookmark, List<BookmarkInfo> bookmarks, int level, PDDocument document) throws IOException {
        while (bookmark != null) {
            int pageNumber = -1; // Default to -1 if page cannot be determined

            String bookmarkTitle = bookmark.getTitle();
            if (bookmarkTitle == null || bookmarkTitle.trim().isEmpty()) {
                bookmarkTitle = "[Untitled Bookmark]";
                logger.warn("Found an untitled bookmark at outline level {}.", level);
            }

            // Determine the page number based on destination or action
            if (bookmark.getDestination() != null) {
                PDPageDestination pageDest = null;

                if (bookmark.getDestination() instanceof PDPageDestination) {
                    pageDest = (PDPageDestination) bookmark.getDestination();
                } else if (bookmark.getDestination() instanceof PDNamedDestination) {
                    PDNamedDestination namedDest = (PDNamedDestination) bookmark.getDestination();
                    pageDest = document.getDocumentCatalog().findNamedDestinationPage(namedDest);
                    if (pageDest == null) {
                        logger.warn("Named destination '{}' could not be resolved to a valid page destination for bookmark '{}'. Page number will be -1.", namedDest.getNamedDestination(), bookmarkTitle);
                    }
                }

                if (pageDest != null) {
                    PDPage actualPage = pageDest.getPage();
                    if (actualPage != null) {
                        pageNumber = document.getPages().indexOf(actualPage) + 1; // PDFBox is 0-indexed
                    } else {
                        int rawPageNum = pageDest.getPageNumber();
                        if (rawPageNum != -1) {
                             pageNumber = rawPageNum + 1;
                        } else {
                            logger.warn("PDPageDestination for bookmark '{}' at level {} returned raw page number -1 and null PDPage.", bookmarkTitle, level);
                        }
                    }
                } else {
                    logger.warn("Unsupported destination type for bookmark '{}' at level {}: {}", bookmarkTitle, level, bookmark.getDestination().getClass().getName());
                }
            } else if (bookmark.getAction() instanceof PDActionGoTo) {
                PDActionGoTo goToAction = (PDActionGoTo) bookmark.getAction();
                if (goToAction.getDestination() != null) {
                    PDPageDestination pageDest = null;
                    if (goToAction.getDestination() instanceof PDPageDestination) {
                        pageDest = (PDPageDestination) goToAction.getDestination();
                    } else if (goToAction.getDestination() instanceof PDNamedDestination) {
                        PDNamedDestination namedDest = (PDNamedDestination) goToAction.getDestination();
                        pageDest = document.getDocumentCatalog().findNamedDestinationPage(namedDest);
                        if (pageDest == null) {
                            logger.warn("Named destination '{}' from GoTo action could not be resolved to a valid page destination for bookmark '{}'. Page number will be -1.", namedDest.getNamedDestination(), bookmarkTitle);
                        }
                    }

                    if (pageDest != null) {
                        PDPage actualPage = pageDest.getPage();
                        if (actualPage != null) {
                            pageNumber = document.getPages().indexOf(actualPage) + 1;
                        } else {
                            int rawPageNum = pageDest.getPageNumber();
                            if (rawPageNum != -1) {
                                pageNumber = rawPageNum + 1;
                            } else {
                                logger.warn("PDPageDestination from GoTo action for bookmark '{}' at level {} returned raw page number -1 and null PDPage.", bookmarkTitle, level);
                            }
                        }
                    } else {
                        logger.warn("Unsupported destination type within GoTo action for bookmark '{}' at level {}: {}", bookmarkTitle, level, goToAction.getDestination().getClass().getName());
                    }
                } else {
                    logger.warn("GoTo action for bookmark '{}' at level {} has no discernable destination.", bookmarkTitle, level);
                }
            } else {
                logger.warn("Unsupported destination or action type for bookmark '{}' at level {}: {}", bookmarkTitle, level,
                            (bookmark.getDestination() != null ? bookmark.getDestination().getClass().getName() :
                             (bookmark.getAction() != null ? bookmark.getAction().getClass().getName() : "null")));
            }

            // Only add bookmarks with a resolved page number
            if (pageNumber != -1) {
                 bookmarks.add(new BookmarkInfo(bookmarkTitle, pageNumber, level));
            } else {
                logger.warn("Bookmark '{}' at level {} could not resolve to a valid page number. Skipping.", bookmarkTitle, level);
            }

            processOutline(bookmark.getFirstChild(), bookmarks, level + 1, document);
            bookmark = bookmark.getNextSibling();
        }
    }

    /**
     * Helper class to store chapter information (title, start page, outline level).
     */
    private static class ChapterInfo {
        private String title;
        private int startPage; // 0-indexed
        private int level;     // 0-indexed

        public ChapterInfo(String title, int startPage, int level) {
            this.title = title;
            this.startPage = startPage;
            this.level = level;
        }

        public String getTitle() {
            return title;
        }

        public int getStartPage() {
            return startPage;
        }

        public int getLevel() {
            return level;
        }
    }
}
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/") // Changed to root to allow flexible sub-paths for different functionalities
public class PdfInfoController {

    private static final Logger logger = LoggerFactory.getLogger(PdfInfoController.class);

    /**
     * Extracts bookmarks (outline items) from a PDF file.
     * @param pdfFile The PDF file to process.
     * @return A list of BookmarkInfo objects.
     */
    @PostMapping(value = "/get/pdf-info/extract-bookmarks", produces = MediaType.APPLICATION_JSON_VALUE)
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

    /**
     * Splits a PDF into multiple smaller PDFs based on a specified bookmark level,
     * and returns them as a ZIP file.
     *
     * @param fileInput The PDF file to split.
     * @param includeMetadata Whether to include metadata (not implemented, placeholder).
     * @param allowDuplicates Whether to allow duplicate pages (not implemented, placeholder).
     * @param bookmarkLevel The outline level at which to split the PDF into chapters (1-indexed).
     * @return A ResponseEntity containing a ZIP file with the split PDF chapters.
     */
    @PostMapping(value = "/get/api/stirling/general/split-pdf-by-chapters", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> splitPdfByChapters(
            @RequestParam("fileInput") MultipartFile fileInput,
            @RequestParam(value = "includeMetadata", defaultValue = "false") boolean includeMetadata, // Placeholder for future use
            @RequestParam(value = "allowDuplicates", defaultValue = "false") boolean allowDuplicates, // Placeholder for future use
            @RequestParam(value = "bookmarkLevel", defaultValue = "1") int bookmarkLevel) {

        if (fileInput == null || fileInput.isEmpty()) {
            logger.warn("Received a request for PDF splitting with an empty or null PDF file.");
            return ResponseEntity.badRequest().body(new byte[0]); // Return empty byte array for bad request
        }
        if (bookmarkLevel < 1) {
            logger.warn("Invalid bookmarkLevel received: {}. Must be 1 or greater.", bookmarkLevel);
            return ResponseEntity.badRequest().body(new byte[0]);
        }

        PDDocument document = null;
        ByteArrayOutputStream zipBos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(zipBos)) {
            document = PDDocument.load(fileInput.getInputStream());
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();

            if (outline == null) {
                logger.info("PDF file '{}' has no document outline. Cannot split by chapters.", fileInput.getOriginalFilename());
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"chapters_no_outline.zip\"")
                        .body(new byte[0]);
            }

            List<BookmarkInfo> allBookmarks = new ArrayList<>();
            processOutline(outline.getFirstChild(), allBookmarks, 0, document);

            // Filter bookmarks to only those at the target bookmarkLevel and sort them by page number
            List<ChapterInfo> chapters = new ArrayList<>();
            // bookmarkLevel is 1-indexed for the user, but internal level in BookmarkInfo is 0-indexed
            for (BookmarkInfo bm : allBookmarks) {
                if (bm.getLevel() == (bookmarkLevel - 1) && bm.getPageNumber() != -1) {
                    // Convert to 0-indexed page for internal processing
                    chapters.add(new ChapterInfo(bm.getTitle(), bm.getPageNumber() - 1, bm.getLevel()));
                }
            }
            chapters.sort(Comparator.comparingInt(ChapterInfo::getStartPage));

            if (chapters.isEmpty()) {
                logger.info("No bookmarks found at level {} in PDF '{}'. Cannot split by chapters.", bookmarkLevel, fileInput.getOriginalFilename());
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"chapters_level_not_found.zip\"")
                        .body(new byte[0]);
            }

            int totalPages = document.getNumberOfPages();
            for (int i = 0; i < chapters.size(); i++) {
                ChapterInfo currentChapter = chapters.get(i);
                int startPageIndex = currentChapter.getStartPage();
                int endPageIndex;

                // Determine the end page for the current chapter
                if (i + 1 < chapters.size()) {
                    // End page is the page *before* the start of the next chapter
                    endPageIndex = chapters.get(i + 1).getStartPage() - 1;
                } else {
                    // If it's the last chapter, it goes to the end of the document
                    endPageIndex = totalPages - 1;
                }

                // Adjust endPageIndex to not exceed document bounds
                endPageIndex = Math.min(endPageIndex, totalPages - 1);

                if (startPageIndex > endPageIndex) {
                    logger.warn("Bookmark '{}' (level {}) points to a page ({}) that is after its calculated end page ({}). Skipping this chapter.",
                            currentChapter.getTitle(), currentChapter.getLevel() + 1, currentChapter.getStartPage() + 1, endPageIndex + 1);
                    continue; // Skip invalid chapter range
                }
                if (startPageIndex >= totalPages || startPageIndex < 0) {
                     logger.warn("Bookmark '{}' (level {}) points to an invalid start page index {}. Skipping this chapter.",
                             currentChapter.getTitle(), currentChapter.getLevel() + 1, startPageIndex);
                     continue;
                }

                PDDocument chapterDoc = null;
                try {
                    chapterDoc = new PDDocument();
                    for (int pageNum = startPageIndex; pageNum <= endPageIndex; pageNum++) {
                        if (pageNum < document.getNumberOfPages()) { // Safety check
                            chapterDoc.addPage(document.getPage(pageNum));
                        } else {
                            logger.warn("Attempted to add page {} which is out of bounds for document '{}' (total pages: {}). Stopping chapter extraction for '{}'.",
                                pageNum + 1, fileInput.getOriginalFilename(), document.getNumberOfPages(), currentChapter.getTitle());
                            break;
                        }
                    }

                    // Clean up title for filename (remove invalid chars)
                    String chapterTitle = currentChapter.getTitle().replaceAll("[^a-zA-Z0-9.\\-_ ]", "").trim();
                    if (chapterTitle.isEmpty()) {
                        chapterTitle = "Chapter_" + (i + 1);
                    }
                    String chapterFileName = String.format("%03d-%s.pdf", i + 1, chapterTitle);

                    ZipEntry zipEntry = new ZipEntry(chapterFileName);
                    zos.putNextEntry(zipEntry);

                    // --- FIX APPLIED HERE: Save to an intermediate ByteArrayOutputStream ---
                    ByteArrayOutputStream chapterPdfBytes = new ByteArrayOutputStream();
                    chapterDoc.save(chapterPdfBytes); // Save PDF content to a temporary buffer
                    zos.write(chapterPdfBytes.toByteArray()); // Write buffer's content to the zip entry
                    // --- END FIX ---

                    zos.closeEntry();
                    logger.debug("Added chapter '{}' (pages {}-{}) to zip.", currentChapter.getTitle(), startPageIndex + 1, endPageIndex + 1);

                } finally {
                    if (chapterDoc != null) {
                        chapterDoc.close();
                    }
                }
            }

            logger.info("Successfully split PDF '{}' into {} chapters at level {}.", fileInput.getOriginalFilename(), chapters.size(), bookmarkLevel);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileInput.getOriginalFilename().replace(".pdf", "_chapters.zip") + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBos.toByteArray());

        } catch (IOException e) {
            logger.error("IOException occurred while splitting PDF file '{}': {}", fileInput.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new byte[0]);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during PDF splitting from '{}': {}", fileInput.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new byte[0]);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    logger.error("Error closing PDF document '{}' after splitting: {}", fileInput.getOriginalFilename(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Helper method to process PDF outline recursively and collect all bookmarks.
     * This method is used by both extractBookmarks and splitPdfByChapters.
     */
    private void processOutline(PDOutlineItem bookmark, List<BookmarkInfo> bookmarks, int level, PDDocument document) throws IOException {
        while (bookmark != null) {
            int pageNumber = -1; // Default to -1 if page cannot be determined

            String bookmarkTitle = bookmark.getTitle();
            if (bookmarkTitle == null || bookmarkTitle.trim().isEmpty()) {
                bookmarkTitle = "[Untitled Bookmark]";
                logger.warn("Found an untitled bookmark at outline level {}.", level);
            }

            // Determine the page number based on destination or action
            if (bookmark.getDestination() != null) {
                PDPageDestination pageDest = null;

                if (bookmark.getDestination() instanceof PDPageDestination) {
                    pageDest = (PDPageDestination) bookmark.getDestination();
                } else if (bookmark.getDestination() instanceof PDNamedDestination) {
                    PDNamedDestination namedDest = (PDNamedDestination) bookmark.getDestination();
                    pageDest = document.getDocumentCatalog().findNamedDestinationPage(namedDest);
                    if (pageDest == null) {
                        logger.warn("Named destination '{}' could not be resolved to a valid page destination for bookmark '{}'. Page number will be -1.", namedDest.getNamedDestination(), bookmarkTitle);
                    }
                }
                // PDPageFitDestination is a subclass of PDPageDestination, so it's handled by the first if block

                if (pageDest != null) {
                    PDPage actualPage = pageDest.getPage();
                    if (actualPage != null) {
                        pageNumber = document.getPages().indexOf(actualPage) + 1; // PDFBox is 0-indexed
                    } else {
                        // Fallback to getPageNumber() if getPage() returns null
                        int rawPageNum = pageDest.getPageNumber();
                        if (rawPageNum != -1) {
                             pageNumber = rawPageNum + 1;
                        } else {
                            logger.warn("PDPageDestination for bookmark '{}' at level {} returned raw page number -1 and null PDPage.", bookmarkTitle, level);
                        }
                    }
                } else {
                    logger.warn("Unsupported destination type for bookmark '{}' at level {}: {}", bookmarkTitle, level, bookmark.getDestination().getClass().getName());
                }
            } else if (bookmark.getAction() instanceof PDActionGoTo) {
                PDActionGoTo goToAction = (PDActionGoTo) bookmark.getAction();
                if (goToAction.getDestination() != null) {
                    PDPageDestination pageDest = null;
                    if (goToAction.getDestination() instanceof PDPageDestination) {
                        pageDest = (PDPageDestination) goToAction.getDestination();
                    } else if (goToAction.getDestination() instanceof PDNamedDestination) {
                        PDNamedDestination namedDest = (PDNamedDestination) goToAction.getDestination();
                        pageDest = document.getDocumentCatalog().findNamedDestinationPage(namedDest);
                        if (pageDest == null) {
                            logger.warn("Named destination '{}' from GoTo action could not be resolved to a valid page destination for bookmark '{}'. Page number will be -1.", namedDest.getNamedDestination(), bookmarkTitle);
                        }
                    }
                    // PDPageFitDestination is a subclass of PDPageDestination, handled above

                    if (pageDest != null) {
                        PDPage actualPage = pageDest.getPage();
                        if (actualPage != null) {
                            pageNumber = document.getPages().indexOf(actualPage) + 1;
                        } else {
                            int rawPageNum = pageDest.getPageNumber();
                            if (rawPageNum != -1) {
                                pageNumber = rawPageNum + 1;
                            } else {
                                logger.warn("PDPageDestination from GoTo action for bookmark '{}' at level {} returned raw page number -1 and null PDPage.", bookmarkTitle, level);
                            }
                        }
                    } else {
                        logger.warn("Unsupported destination type within GoTo action for bookmark '{}' at level {}: {}", bookmarkTitle, level, goToAction.getDestination().getClass().getName());
                    }
                } else {
                    logger.warn("GoTo action for bookmark '{}' at level {} has no discernable destination.", bookmarkTitle, level);
                }
            } else {
                logger.warn("Unsupported destination or action type for bookmark '{}' at level {}: {}", bookmarkTitle, level,
                            (bookmark.getDestination() != null ? bookmark.getDestination().getClass().getName() :
                             (bookmark.getAction() != null ? bookmark.getAction().getClass().getName() : "null")));
            }

            // Only add bookmarks with a resolved page number
            if (pageNumber != -1) {
                 bookmarks.add(new BookmarkInfo(bookmarkTitle, pageNumber, level));
            } else {
                logger.warn("Bookmark '{}' at level {} could not resolve to a valid page number. Skipping.", bookmarkTitle, level);
            }

            processOutline(bookmark.getFirstChild(), bookmarks, level + 1, document);
            bookmark = bookmark.getNextSibling();
        }
    }

    /**
     * Helper class to store chapter information (title, start page, outline level).
     */
    private static class ChapterInfo {
        private String title;
        private int startPage; // 0-indexed
        private int level;     // 0-indexed

        public ChapterInfo(String title, int startPage, int level) {
            this.title = title;
            this.startPage = startPage;
            this.level = level;
        }

        public String getTitle() {
            return title;
        }

        public int getStartPage() {
            return startPage;
        }

        public int getLevel() {
            return level;
        }
    }
}
