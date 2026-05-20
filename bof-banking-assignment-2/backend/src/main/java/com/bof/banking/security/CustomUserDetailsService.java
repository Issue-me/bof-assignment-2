package com.bof.banking.security;

import com.bof.banking.model.User;
import com.bof.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Custom UserDetailsService that loads users from the database for Spring Security.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    /**
     * Returns user by username data.
     * @param email the email of the authenticated user.
     * @return the result of the operation.
     * @throws UsernameNotFoundException  if the operation cannot be completed
     */
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if (!user.isActive()) {
            throw new UsernameNotFoundException("Account is disabled: " + email);
        }

        return new CustomUserDetails(user);
    }
}
