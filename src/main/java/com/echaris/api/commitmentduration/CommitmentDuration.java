package com.echaris.api.commitmentduration;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "commitment_durations")
public class CommitmentDuration extends BaseEntity {

    private int durationMonths;
    private boolean active;

    @Version
    private Long version;
}