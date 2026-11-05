package com.echaris.api.utils.fileupload;




import com.echaris.api.utils.fileupload.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;

public interface FileUploadService {

    FileUploadResponse uploadFile(
            MultipartFile file,
            FileUploadCategory category,
            FileUsageType usageType
    );

    ManagedFile getFileByUrl(String fileUrl);

    void deleteFileByUrl(String fileUrl);

    void deleteOldFileIfChanged(String oldFileUrl, String newFileUrl);

    void deleteFileAsync(String fileUrl);

    void deleteFileAsync(Collection<String> fileUrls);

    long countAllMediaAssets();

    long countMediaAssetsByCategory(FileUploadCategory category);

    long countMediaAssetsByUsageType(FileUsageType usageType);
}