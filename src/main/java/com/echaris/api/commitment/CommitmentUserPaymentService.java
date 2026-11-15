package com.echaris.api.commitment;

import com.echaris.api.admin.AdminRepository;
import com.echaris.api.audit.AuditService;
import com.echaris.api.commitmentduration.CommitmentDurationService;
import com.echaris.api.commitmentrate.CommitmentRateService;
import com.echaris.api.customer.CustomerRepository;
import com.echaris.api.notification.NotificationService;
import com.echaris.api.userauthmgt.user.UserRepository;
import com.echaris.api.utils.emailsenderservice.EmailSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommitmentUserPaymentService {



    private final MongoTemplate mongoTemplate;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final CommitmentRateService rateService;
    private final CommitmentDurationService durationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailSenderService emailSenderService;
}
