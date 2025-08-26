package com.igsl.group.email_service_sample.exception;

public class FolderNotFoundException extends RuntimeException {
    public FolderNotFoundException(String folderName) {
        super("Folder not found: " + folderName);
    }
    
    public FolderNotFoundException(Long folderId) {
        super("Folder not found with ID: " + folderId);
    }
}