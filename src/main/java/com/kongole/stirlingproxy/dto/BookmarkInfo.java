package com.kongole.stirlingproxy.dto;

public class BookmarkInfo {
    private String title;
    private int pageNumber; // 1-based page number

    public BookmarkInfo(String title, int pageNumber) {
        this.title = title;
        this.pageNumber = pageNumber;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    // You might want to override toString(), equals(), hashCode() for good practice
}
