package com.echaris.api.utils.fileupload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.echaris.api.utils.exception.ErrorProcessingRequestException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import com.echaris.api.utils.fileupload.response.FileUploadResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@Log4j2
@RequiredArgsConstructor
public class CloudinaryFileUploadServiceImpl implements FileUploadService {

    @Value("${app.cloudinary-cloud}")
    private String cloudName;

    @Value("${app.cloudinary-key}")
    private String apiKey;

    @Value("${app.cloudinary-secret}")
    private String apiSecret;

    private Cloudinary cloudinary;

    private final ManagedFileRepository managedFileRepository;


    @PostConstruct
    private void initializeCloudinary() {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    @Override
    public FileUploadResponse uploadFile(
            MultipartFile multipartFile,
            FileUploadCategory category,
            FileUsageType usageType
    ) {
        validateFile(multipartFile);

        FileUploadCategory resolvedCategory = category == null ? FileUploadCategory.OTHER : category;
        FileUsageType resolvedUsageType = usageType == null ? FileUsageType.GENERAL : usageType;

        Map uploadResult = uploadToCloudinary(multipartFile, resolvedCategory, resolvedUsageType);

        ManagedFile managedFile = new ManagedFile();
        managedFile.setOriginalFileName(multipartFile.getOriginalFilename());
        managedFile.setFileUrl((String) uploadResult.get("secure_url"));
        managedFile.setSecureUrl((String) uploadResult.get("secure_url"));
        managedFile.setPublicId((String) uploadResult.get("public_id"));
        managedFile.setFormat((String) uploadResult.get("format"));
        managedFile.setResourceType((String) uploadResult.get("resource_type"));
        managedFile.setFileSizeBytes(getLongValue(uploadResult.get("bytes")));
        managedFile.setCategory(resolvedCategory);
        managedFile.setUsageType(resolvedUsageType);

        ManagedFile savedFile = managedFileRepository.save(managedFile);


        return mapToResponse(savedFile);
    }

    @Override
    public ManagedFile getFileByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }

        return managedFileRepository.findByFileUrl(fileUrl)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    @Override
    public void deleteOldFileIfChanged(String oldFileUrl, String newFileUrl) {
        if (oldFileUrl == null || oldFileUrl.isBlank()) {
            return;
        }

        if (newFileUrl == null || newFileUrl.isBlank()) {
            return;
        }

        if (oldFileUrl.equals(newFileUrl)) {
            return;
        }

        deleteFileByUrl(oldFileUrl);
    }

    @Override
    public void deleteFileByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        ManagedFile file = managedFileRepository.findByFileUrl(fileUrl)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        deleteFromCloudinary(file.getPublicId(), file.getResourceType());

        managedFileRepository.delete(file);
    }

    @Override
    @Async
    public void deleteFileAsync(String fileUrl) {
        try {
            deleteFileByUrl(fileUrl);
        } catch (Exception exception) {
            log.error("Error deleting file async: {}", fileUrl, exception);
        }
    }

    @Override
    @Async
    public void deleteFileAsync(Collection<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return;
        }

        for (String fileUrl : fileUrls) {
            try {
                deleteFileByUrl(fileUrl);
            } catch (Exception exception) {
                log.error("Error deleting file async: {}", fileUrl, exception);
            }
        }
    }

    @Override
    public long countAllMediaAssets() {
        return managedFileRepository.count();
    }

    @Override
    public long countMediaAssetsByCategory(FileUploadCategory category) {
        if (category == null) {
            return 0;
        }

        return managedFileRepository.countByCategory(category);
    }

    @Override
    public long countMediaAssetsByUsageType(FileUsageType usageType) {
        if (usageType == null) {
            return 0;
        }

        return managedFileRepository.countByUsageType(usageType);
    }

    private Map uploadToCloudinary(
            MultipartFile multipartFile,
            FileUploadCategory category,
            FileUsageType usageType
    ) {
        File file = null;

        try {
            file = File.createTempFile(
                    "mandate-sport-",
                    "-" + sanitizeFileName(multipartFile.getOriginalFilename())
            );

            multipartFile.transferTo(file);

            Map<String, Object> options = new HashMap<>();
            options.put("folder", buildFolderPath(category, usageType));
            options.put("resource_type", "auto");
            options.put("access_mode", "public");

            return cloudinary.uploader().upload(file, options);

        } catch (Exception exception) {
            log.error("Error uploading file to Cloudinary", exception);
            throw new ErrorProcessingRequestException("Error uploading file, please try again later");

        } finally {
            if (file != null && file.exists()) {
                boolean deleted = file.delete();

                if (!deleted) {
                    log.warn("Temporary upload file could not be deleted: {}", file.getAbsolutePath());
                }
            }
        }
    }

    private void deleteFromCloudinary(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }

        try {
            Map result = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", resolveResourceType(resourceType))
            );

            log.info("Cloudinary delete result for {}: {}", publicId, result);

        } catch (Exception exception) {
            log.error("Error deleting file from Cloudinary: {}", publicId, exception);
            throw new ErrorProcessingRequestException("Error deleting file from Cloudinary");
        }
    }


    private String buildFolderPath(FileUploadCategory category, FileUsageType usageType) {
        String categoryFolder = category == null ? "other" : category.name().toLowerCase();
        String usageFolder = usageType == null ? "general" : usageType.name().toLowerCase();
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        return "mandate-sports/" + categoryFolder + "/" + usageFolder + "/" + dateFolder;
    }

    private String resolveResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return "image";
        }

        return resourceType;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ErrorProcessingRequestException("File is required");
        }
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "file";
        }

        return originalFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }

    private Long getLongValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        }

        if (value instanceof Long longValue) {
            return longValue;
        }

        return Long.parseLong(value.toString());
    }

    private FileUploadResponse mapToResponse(ManagedFile file) {
        return new FileUploadResponse(
                file.getFileUrl(),
                file.getPublicId(),
                file.getOriginalFileName(),
                file.getFormat(),
                file.getResourceType(),
                file.getFileSizeBytes(),
                file.getCategory(),
                file.getUsageType()
        );
    }
}