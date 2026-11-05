package com.echaris.api.utils.fileupload;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ManagedFileRepository extends MongoRepository<ManagedFile, String> {

    Optional<ManagedFile> findByFileUrl(String fileUrl);

    Optional<ManagedFile> findByPublicId(String publicId);

    List<ManagedFile> findByCategory(FileUploadCategory category);

    List<ManagedFile> findByUsageType(FileUsageType usageType);

    boolean existsByFileUrl(String fileUrl);

    long countByCategory(FileUploadCategory category);

    long countByUsageType(FileUsageType usageType);


    long countByCreatedAtBetween(
            java.time.LocalDateTime start,
            java.time.LocalDateTime end
    );

}