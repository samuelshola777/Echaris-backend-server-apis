package com.echaris.api.utils;




import com.echaris.api.utils.fileupload.FileUploadCategory;
import com.echaris.api.utils.fileupload.FileUploadService;
import com.echaris.api.utils.fileupload.FileUsageType;
import com.echaris.api.utils.fileupload.response.FileUploadResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/utilities")
@AllArgsConstructor
public class UtilityController {

    private final FileUploadService fileUploadService;

    @PostMapping("/public/file/upload")
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") FileUploadCategory category,
            @RequestParam(value = "usageType", required = false) FileUsageType usageType
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "File uploaded successfully",
                fileUploadService.uploadFile(file, category, usageType)
        ));
    }
}