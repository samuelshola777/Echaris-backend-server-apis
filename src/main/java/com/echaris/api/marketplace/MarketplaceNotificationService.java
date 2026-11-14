package com.echaris.api.marketplace;

import com.echaris.api.address.AddressType;
import com.echaris.api.admin.Admin;
import com.echaris.api.admin.AdminRepository;
import com.echaris.api.notification.NotificationService;
import com.echaris.api.product.Product;
import com.echaris.api.product.ProductRepository;
import com.echaris.api.userPayment.UserPayment;
import com.echaris.api.userPayment.UserPaymentStatus;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserRepository;
import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.utils.emailsenderservice.EmailSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MarketplaceNotificationService {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailSenderService emailSenderService;
    private final ProductRepository productRepository;

    private String getProductName(MarketplaceFinancing financing) {
        return productRepository.findById(financing.getProductId())
                .map(Product::getName)
                .filter(name -> !name.isBlank())
                .orElse(financing.getName());
    }

    void sendPaymentAwaitingApproval(MarketplaceFinancing financing, UserPayment payment) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("paymentId", payment.getId());
        data.put("targetId", financing.getId());
        data.put("financingId", financing.getId());
        data.put("financingName", financing.getName());
        data.put("customerUserId", financing.getUserId());
        data.put("purpose", payment.getPurpose().name());
        data.put("direction", payment.getDirection().name());
        data.put("amount", payment.getAmount().toPlainString());
        data.put("currency", "NGN");
        data.put("status", payment.getStatus().name());
        data.put("receiptCount", Integer.toString(payment.getReceiptUrls().size()));
        if (payment.getBankName() != null) {
            data.put("bankName", payment.getBankName());
        }
        if (payment.getAccountName() != null) {
            data.put("accountName", payment.getAccountName());
        }
        data.put("submittedAt", payment.getCreatedAt().toString());
        if (payment.getTransferReference() != null) {
            data.put("transferReference", payment.getTransferReference());
        }
        String accountNumber = payment.getAccountNumber();
        if (accountNumber != null && accountNumber.length() >= 4) {
            data.put("accountNumberLast4", accountNumber.substring(accountNumber.length() - 4));
        }

        String description = "NGN %s payment for marketplace financing \"%s\""
                .formatted(payment.getAmount().toPlainString(), financing.getName());
        String customerTitle = "Marketplace payment under review";
        String customerMessage = "We received your %s. We'll notify you as soon as your payment is approved."
                .formatted(description);

        User customer = userRepository.findById(financing.getUserId())
                .filter(user -> !user.isDeleted())
                .orElse(null);
        if (customer != null) {
            notificationService.create(customer.getId(), "USER_PAYMENT_UNDER_REVIEW",
                    customerTitle, customerMessage, "USER_PAYMENT", payment.getId(), data);
            sendEmail(customer, customerTitle, customerMessage);
        }

        Map<String, String> adminData = new LinkedHashMap<>(data);
        adminData.put("detailsPath", "/api/v1/user-payments/private/admin/" + payment.getId());
        String customerEmail = customer == null ? "" : Objects.toString(customer.getEmailAddress(), "");
        if (!customerEmail.isBlank()) {
            adminData.put("customerEmail", customerEmail);
        }
        String adminTitle = "Marketplace payment awaiting approval";
        String adminMessage = "%s submitted a %s. Open the payment details to review the receipts."
                .formatted(adminData.getOrDefault("customerEmail", financing.getUserId()), description);
        Map<String, String> emailData = new LinkedHashMap<>(adminData);
        emailData.put("accountNumber", payment.getAccountNumber());
        emailData.put("transferReference", Objects.toString(payment.getTransferReference(), "Not supplied"));
        emailData.put("receiptUrls", String.join("\n", payment.getReceiptUrls()));

        activeAdmins().forEach(admin -> {
            notificationService.create(admin.getId(), "USER_PAYMENT_SUBMITTED", adminTitle,
                    adminMessage, "USER_PAYMENT", payment.getId(), adminData);
            sendEmail(admin, adminTitle, "user-payment-submitted", emailData);
        });
    }

    void sendPaymentDecision(MarketplaceFinancing financing, UserPayment payment) {
        boolean approved = payment.getStatus() == UserPaymentStatus.APPROVED;
        String type = approved ? "USER_PAYMENT_APPROVED" : "USER_PAYMENT_REJECTED";
        String title = approved ? "Marketplace payment approved" : "Marketplace payment rejected";
        String product = getProductName(financing);
        String description = "NGN %s payment for %s".formatted(payment.getAmount().toPlainString(), product);
        String message = approved
                ? "Your %s was approved. Your marketplace financing is now accumulating returns.".formatted(description)
                : "Your %s was rejected. Reason: %s".formatted(description, payment.getReasonForRejection());

        Map<String, String> data = new LinkedHashMap<>();
        data.put("paymentId", payment.getId());
        data.put("targetId", financing.getId());
        data.put("financingId", financing.getId());
        data.put("financingName", financing.getName());
        data.put("productName", product);
        data.put("amount", payment.getAmount().toPlainString());
        data.put("currency", "NGN");
        data.put("status", payment.getStatus().name());
        if (!approved) {
            data.put("reasonForRejection", payment.getReasonForRejection());
        }

        userRepository.findById(payment.getUserId())
                .filter(user -> !user.isDeleted())
                .ifPresent(customer -> {
                    notificationService.create(customer.getId(), type, title, message,
                            "USER_PAYMENT", payment.getId(), data);
                    sendEmail(customer, title, message);
                });
    }

    void sendProductRelease(MarketplaceFinancing financing) {
        MarketplaceFulfillment fulfillment = financing.getFulfillment();
        boolean pickup = fulfillment.type() == AddressType.PICKUP;
        String method = pickup ? "pickup" : "delivery";
        String product = getProductName(financing);
        String address = fulfillment.address();
        String adminMessage = "Prepare " + financing.getProductQuantity() + " × " + product + " for " + method + " at "
                + address + (pickup ? "." : ". Contact: " + fulfillment.phoneNumber() + ".");

        notifyCustomerAndAdmins(financing, product, "MARKETPLACE_PRODUCT_RELEASED", "Product ready for " + method + " preparation",
                "Your " + product + " has reached the release milestone and can now be prepared for " + method
                        + " at " + address + ".", adminMessage);
    }

    void sendFinancingMatured(MarketplaceFinancing financing) {
        String product = getProductName(financing);
        notifyCustomer(financing, product, "MARKETPLACE_MATURED", "Marketplace capital available",
                "Returns for " + product + " have reached the product total. "
                        + "You can now request your original capital.");
    }

    void sendCapitalRequest(MarketplaceFinancing financing) {
        String product = getProductName(financing);
        notifyCustomerAndAdmins(financing, product, "MARKETPLACE_CAPITAL_REQUESTED", "Marketplace capital requested",
                "We received your capital payout request for " + product + ".",
                "A customer requested their marketplace capital payout for " + product + ".");
    }

    void sendFulfillmentUpdate(MarketplaceFinancing financing) {
        String product = getProductName(financing);
        String message = switch (financing.getFulfillmentStatus()) {
            case PREPARING -> "We started preparing your " + product + ".";
            case PACKAGED -> "Your " + product + " has been packaged.";
            case IN_TRANSIT -> "Your " + product + " is on its way.";
            case DELIVERED -> "Your " + product + " was delivered.";
            case READY_FOR_PICKUP -> "Your " + product + " is ready for pickup at "
                    + financing.getFulfillment().address() + ".";
            case COLLECTED -> "Your " + product + " was collected.";
            default -> throw new IllegalStateException("Status is managed by the return scheduler");
        };

        notifyCustomer(financing, product, "MARKETPLACE_FULFILLMENT_UPDATED", "Product fulfillment updated", message);
    }

    private void notifyCustomer(MarketplaceFinancing financing, String productName, String type,
                                String title, String message) {
        userRepository.findById(financing.getUserId())
                .filter(user -> !user.isDeleted())
                .ifPresent(user -> send(user, financing, productName, type, title, message, false));
    }

    private void notifyCustomerAndAdmins(MarketplaceFinancing financing, String productName, String type,
                                         String title, String customerMessage, String adminMessage) {
        userRepository.findById(financing.getUserId())
                .filter(user -> !user.isDeleted())
                .ifPresent(user -> send(user, financing, productName, type, title, customerMessage, true));

        activeAdmins().forEach(admin -> send(admin, financing, productName, type, title, adminMessage, true));
    }

    private Stream<User> activeAdmins() {
        var adminIds = adminRepository.findAllByDeleted(false).stream()
                .filter(Admin::isActive)
                .map(Admin::getUserId)
                .distinct()
                .toList();

        return userRepository.findAllById(adminIds).stream()
                .filter(admin -> admin.getUserType() == UserType.ADMIN && admin.isActive()
                        && admin.isVerified() && !admin.isDeleted());
    }

    private void send(User user, MarketplaceFinancing financing, String productName, String type,
                      String title, String message, boolean email) {
        MarketplaceFulfillment fulfillment = financing.getFulfillment();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("financingId", financing.getId());
        data.put("customerUserId", financing.getUserId());
        data.put("productId", financing.getProductId());
        data.put("productName", productName);
        data.put("quantity", Integer.toString(financing.getProductQuantity()));
        data.put("fulfillmentType", fulfillment.type().name());
        data.put("fulfillmentStatus", financing.getFulfillmentStatus().name());
        data.put("address", fulfillment.address());
        if (fulfillment.pickupLocationId() != null) {
            data.put("pickupLocationId", fulfillment.pickupLocationId());
        }
        if (fulfillment.phoneNumber() != null) {
            data.put("phoneNumber", fulfillment.phoneNumber());
        }
        String path = user.getUserType() == UserType.ADMIN ? "/private/admin/" : "/private/";
        data.put("detailsPath", "/api/v1/marketplace" + path + financing.getId());

        notificationService.create(user.getId(), type, title, message, "MARKETPLACE_FINANCING",
                financing.getId(), data);

        if (email) {
            sendEmail(user, title, message);
        }
    }

    private void sendEmail(User user, String title, String message) {
        sendEmail(user, title, "marketplace-event", Map.of(
                "name", user.getFirstName() == null ? "there" : user.getFirstName(),
                "title", title, "message", message));
    }

    private void sendEmail(User user, String title, String template, Map<String, String> data) {
        if (user.getEmailAddress() != null && !user.getEmailAddress().isBlank()) {
            emailSenderService.sendTemplateEmailAfterCommit(user.getEmailAddress(), title, template, data);
        }
    }
}