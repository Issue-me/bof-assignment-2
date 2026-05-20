package com.bof.banking.mapper;

import com.bof.banking.dto.user.UserResponse;
import com.bof.banking.model.User;
import org.springframework.stereotype.Component;

/**
 * Mapper for User entity to DTO conversions.
 */
@Component
public class UserMapper {

    /**
     * Handles to response.
     * @param user the authenticated user context.
     * @return the result of the operation.
     */
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .customerId(user.getCustomerId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .role(user.getRole().name())
                .resident(user.isResident())
                .seniorCitizen(user.isSeniorCitizen())
                .tinNumber(user.getTinNumber())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }
}
