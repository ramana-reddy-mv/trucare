package com.trucare.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class TruCareOpenApiConfiguration {
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenApiCustomizer truCareBearerScheme() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            components.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
        };
    }

    @Bean
    OperationCustomizer truCareBearerRequirement() {
        return (operation, handlerMethod) -> {
            String roles = requiredRoles(handlerMethod);
            operation.setDescription((operation.getDescription() == null ? "" : operation.getDescription() + "\n\n")
                    + "Authorization: " + roles + ".");
            if (!roles.equals("No authentication required")) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
            }
            return operation;
        };
    }

    private String requiredRoles(HandlerMethod handler) {
        String controller = handler.getBeanType().getSimpleName();
        String method = handler.getMethod().getName();
        return switch (controller) {
            case "AuthController" -> "No authentication required";
            case "MemberController" -> switch (method) {
                case "validateMember" -> "SERVICE";
                case "getMemberByUsername" -> "MEMBER, ADMIN, or SERVICE (members may access only their own profile)";
                case "createMember" -> "PENDING_MEMBER, ADMIN, or SERVICE";
                default -> "ADMIN or SERVICE";
            };
            case "ProviderController" -> method.equals("validateProvider") ? "SERVICE" : "ADMIN or SERVICE";
            case "ClaimController" -> switch (method) {
                case "createClaim" -> "PENDING_MEMBER, CLAIM_OFFICER, SERVICE, or ADMIN";
                case "getClaimByBusinessId" -> "SERVICE";
                case "getClaimById" -> "CLAIM_OFFICER or ADMIN";
                case "removeClaim" -> "CLAIM_OFFICER or ADMIN";
                default -> "CLAIM_OFFICER or ADMIN";
            };
            case "PolicyController" -> switch (method) {
                case "checkEligibility" -> "SERVICE";
                case "getPolicy", "listPolicies", "getPolicyByNumber", "listPolicyMembers",
                        "listMemberPolicies", "getCoverage" -> "ADMIN, CLAIM_OFFICER, or SERVICE";
                default -> "ADMIN or SERVICE";
            };
            case "DocumentController", "CareController" -> "ADMIN, SERVICE, or CLAIM_OFFICER";
            case "SettlementController" -> switch (method) {
                case "updatePaymentStatus" -> "PAYMENT_SERVICE or SERVICE";
                case "getById", "getByNumber", "getByClaimId", "search" ->
                        "ADMIN, SETTLEMENT_OFFICER, SETTLEMENT_OPERATOR, PAYMENT_SERVICE, or SERVICE";
                default -> "ADMIN, SETTLEMENT_OFFICER, or SETTLEMENT_OPERATOR";
            };
            case "PaymentController" -> switch (method) {
                case "reconcile", "retryCallbacks" -> "ADMIN, PAYMENT_SERVICE, or PAYMENT_INTERNAL";
                default -> "ADMIN, PAYMENT_OFFICER, or PAYMENT_OPERATOR";
            };
            case "NotificationController" -> switch (method) {
                case "create" -> "SERVICE or NOTIFICATION_INTERNAL";
                case "retry" -> "SERVICE, ADMIN, NOTIFICATION_INTERNAL, or NOTIFICATION_ADMIN";
                case "getByReference", "search" -> "SERVICE, ADMIN, NOTIFICATION_INTERNAL, or NOTIFICATION_ADMIN";
                default -> "Authenticated user (ownership is checked by the controller where applicable)";
            };
            case "NotificationPreferenceController" -> "Authenticated user (ownership is checked by the controller)";
            case "NotificationTemplateController" -> "ADMIN or NOTIFICATION_ADMIN";
            default -> "Authenticated user; see the service security policy for role restrictions";
        };
    }
}
