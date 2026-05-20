package com.bof.banking.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for login/register response with JWT token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String message;
}
