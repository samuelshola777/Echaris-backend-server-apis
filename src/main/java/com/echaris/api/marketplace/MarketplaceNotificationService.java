package com.echaris.api.marketplace;

import com.echaris.api.address.AddressType;
import com.echaris.api.admin.Admin;
import com.echaris.api.admin.AdminRepository;
import com.echaris.api.notification.NotificationService;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserRepository;
import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.utils.emailsenderservice.EmailSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketplaceNotificationService {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailSenderService emailSenderService;

    void sendProductRelease(MarketplaceFinancing financing) {
        MarketplaceFulfillment fulfillment = financing.getFulfillment();
        boolean pickup = fulfillment.type() == AddressType.PICKUP;
        String method = pickup ? "pickup" : "delivery";
        String product = financing.getProductName();
        String address = fulfillment.address();
        String adminMessage = "Prepare " + financing.getQuantity() + " × " + product + " for " + method + " at "
                + address + (pickup ? "." : ". Contact: " + fulfillment.phoneNumber() + ".");

        notifyCustomerAndAdmins(financing, "MARKETPLACE_PRODUCT_RELEASED", "Product ready for " + method + " preparation",
                "Your " + product + " has reached the release milestone and can now be prepared for " + method
                        + " at " + address + ".", adminMessage);
    }

    void sendFinancingMatured(MarketplaceFinancing financing) {
        notifyCustomer(financing, "MARKETPLACE_MATURED", "Marketplace capital available",
                "Returns for " + financing.getProductName() + " have reached the product total. "
                        + "You can now request your original capital.");
    }

    void sendCapitalRequest(MarketplaceFinancing financing) {
        String product = financing.getProductName();
        notifyCustomerAndAdmins(financing, "MARKETPLACE_CAPITAL_REQUESTED", "Marketplace capital requested",
                "We received your capital payout request for " + product + ".",
                "A customer requested their marketplace capital payout for " + product + ".");
    }

    void sendFulfillmentUpdate(MarketplaceFinancing financing) {
        String product = financing.getProductName();
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

        notifyCustomer(financing, "MARKETPLACE_FULFILLMENT_UPDATED", "Product fulfillment updated", message);
    }

    private void notifyCustomer(MarketplaceFinancing financing, String type, String title, String message) {
        userRepository.findById(financing.getUserId())
                .filter(user -> !user.isDeleted())
                .ifPresent(user -> send(user, financing, type, title, message, false));
    }

    private void notifyCustomerAndAdmins(MarketplaceFinancing financing, String type, String title,
                                         String customerMessage, String adminMessage) {
        userRepository.findById(financing.getUserId())
                .filter(user -> !user.isDeleted())
                .ifPresent(user -> send(user, financing, type, title, customerMessage, true));

        var adminIds = adminRepository.findAllByDeleted(false).stream()
                .filter(Admin::isActive)
                .map(Admin::getUserId)
                .distinct()
                .toList();

        for (User admin : userRepository.findAllById(adminIds)) {
            if (admin.getUserType() == UserType.ADMIN && admin.isActive() && admin.isVerified() && !admin.isDeleted()) {
                send(admin, financing, type, title, adminMessage, true);
            }
        }
    }

    private void send(User user, MarketplaceFinancing financing, String type, String title,
                      String message, boolean sendEmail) {
        MarketplaceFulfillment fulfillment = financing.getFulfillment();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("financingId", financing.getId());
        data.put("customerUserId", financing.getUserId());
        data.put("productId", financing.getProductId());
        data.put("productName", financing.getProductName());
        data.put("quantity", Integer.toString(financing.getQuantity()));
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

        if (sendEmail && user.getEmailAddress() != null && !user.getEmailAddress().isBlank()) {
            emailSenderService.sendTemplateEmailAfterCommit(user.getEmailAddress(), title, "marketplace-event",
                    Map.of("name", user.getFirstName() == null ? "there" : user.getFirstName(),
                            "title", title, "message", message));
        }
    }
}