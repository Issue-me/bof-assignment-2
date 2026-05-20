package com.bof.banking.security;

import com.bof.banking.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Custom UserDetails implementation wrapping the User entity.
 */
@Data
@Builder
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final User user;

    @Override
    /**
     * Returns authorities data.
     * @return the result of the operation.
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    /**
     * Returns password data.
     * @return the resulting text value.
     */
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    /**
     * Returns username data.
     * @return the resulting text value.
     */
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    /**
     * Checks whether account non expired is valid.
     * @return true if the condition is met; otherwise false.
     */
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    /**
     * Checks whether account non locked is valid.
     * @return true if the condition is met; otherwise false.
     */
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    /**
     * Checks whether credentials non expired is valid.
     * @return true if the condition is met; otherwise false.
     */
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    /**
     * Checks whether enabled is valid.
     * @return true if the condition is met; otherwise false.
     */
    public boolean isEnabled() {
        return user.isActive();
    }

    /**
     * Returns user id data.
     * @return the result of the operation.
     */
    public Long getUserId() {
        return user.getId();
    }

    /**
     * Returns customer id data.
     * @return the resulting text value.
     */
    public String getCustomerId() {
        return user.getCustomerId();
    }
}
