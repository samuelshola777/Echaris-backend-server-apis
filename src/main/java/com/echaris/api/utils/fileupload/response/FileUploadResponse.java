package com.echaris.api.utils.fileupload.response;


import com.echaris.api.utils.fileupload.FileUploadCategory;
import com.echaris.api.utils.fileupload.FileUsageType;

public record FileUploadResponse(
        String fileUrl,
        String publicId,
        String originalFileName,
        String format,
        String resourceType,
        Long fileSizeBytes,
        FileUploadCategory category,
        FileUsageType usageType
) {
}