package com.echaris.api.utils;



import com.echaris.api.utils.exception.InvalidInputException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaginationService {

    private final MongoTemplate mongoTemplate;

    // API page numbers start at 1.
    // Pass an unsorted query containing only the filters.
    public <T> PaginatedData paginate(
            Query filter,
            Class<T> entityType,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Set<String> allowedSortFields
    ) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException(
                    "Page must be 1-10000 and size must be 1-100"
            );
        }

        if (entityType == null) {
            throw new IllegalArgumentException("Entity type is required");
        }

        if (filter != null && filter.isSorted()) {
            throw new IllegalArgumentException(
                    "Pass an unsorted filter; use sortBy and sortDirection"
            );
        }

        String field = sortBy == null || sortBy.isBlank()
                ? "createdAt"
                : sortBy.trim();

        if (allowedSortFields == null || !allowedSortFields.contains(field)) {
            throw new InvalidInputException("Unsupported sort field");
        }

        Sort.Direction direction;

        try {
            direction = sortDirection == null || sortDirection.isBlank()
                    ? Sort.Direction.DESC
                    : Sort.Direction.valueOf(
                    sortDirection.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(
                    "Sort direction must be ASC or DESC"
            );
        }

        Sort sort = Sort.by(direction, field);

        if (!field.equals("id") && !field.equals("_id")) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }

        PageRequest pageable = PageRequest.of(page - 1, size, sort);

        // Copy the filter instead of changing the caller's query.
        Query countQuery = Query.of(
                filter == null ? new Query() : filter
        ).skip(0).limit(0);

        Query dataQuery = Query.of(countQuery).with(pageable);

        List<T> content = mongoTemplate.find(dataQuery, entityType);

        Page<T> result = PageableExecutionUtils.getPage(
                content,
                pageable,
                () -> mongoTemplate.count(countQuery, entityType)
        );

        return new PaginatedData(
                result.getTotalPages(),
                result.getNumberOfElements(),
                result.getTotalElements(),
                result.getContent()
        );
    }
}