package com.bof.banking.service.impl;

import com.bof.banking.dto.user.UserResponse;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.mapper.UserMapper;
import com.bof.banking.model.User;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Encapsulates business rules for u se rs er vi ce im pl and keeps controller logic thin.
 */
public class UserServiceImpl implements UserService {

    private final UserRepository     userRepository;
    private final UserMapper         userMapper;
    private final NrwhtRefundService nrwhtRefundService;

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns user by id data.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns user by email data.
     * @param email the email of the authenticated user.
     * @return the result of the operation.
     */
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns user by customer id data.
     * @param customerId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public UserResponse getUserByCustomerId(String customerId) {
        User user = userRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with customerId: " + customerId));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns all users data.
     * @return the matching results.
     */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    /**
     * Updates user values.
     * @param id the unique identifier of the target record.
     * @param userResponse the user Response.
     * @return the result of the operation.
     */
    public UserResponse updateUser(Long id, UserResponse userResponse) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Capture state BEFORE the update — used to decide whether refund fires
        boolean hadTin      = user.getTinNumber() != null && !user.getTinNumber().isBlank();
        boolean wasResident = user.isResident();

        // Apply updates
        user.setFirstName(userResponse.getFirstName());
        user.setLastName(userResponse.getLastName());
        user.setPhoneNumber(userResponse.getPhoneNumber());
        user.setTinNumber(userResponse.getTinNumber());
        user.setResident(userResponse.isResident());
        user.setSeniorCitizen(userResponse.isSeniorCitizen());

        User savedUser = userRepository.save(user);
        log.info("User {} updated successfully", savedUser.getEmail());

        

        // ── NRWHT refund check ────────────────────────────────────────────────
        // Fires ONLY when all three conditions are true:
        //   1. User WAS a Fiji resident before this update (NRWHT applies to
        //      residents without TIN — so they were being charged NRWHT)
        //   2. User did NOT have a TIN before this update (→ NRWHT was deducted)
        //   3. User NOW has a TIN after this update (→ switch to RIWT going forward,
        //      refund the NRWHT charged so far this year)
        //
        // Does NOT fire for:
        //   - Non-residents registering a TIN (NRWHT applies regardless of TIN)
        //   - Users who already had a TIN and are just changing it
        //   - Residency-only changes (no TIN involved)
        boolean nowHasTin = savedUser.getTinNumber() != null
                    && !savedUser.getTinNumber().isBlank();
        boolean isResidentNow = savedUser.isResident();

        log.error("DEBUG REFUND → wasResident={} nowResident={} hadTin={} nowHasTin={} user={}",
        wasResident, isResidentNow, hadTin, nowHasTin, savedUser.getEmail());

        // 🔍 DEBUG LOG
        log.warn("Refund condition check → wasResident={} nowResident={} hadTin={} nowHasTin={}",
            wasResident, isResidentNow, hadTin, nowHasTin);

        if (!hadTin && nowHasTin && isResidentNow) {
            String updatedBy = resolveCurrentPrincipal();

            // KEY: pass the user's DB id (Long), NOT the User object.
            //
            // NrwhtRefundService runs in Propagation.REQUIRES_NEW and calls
            // entityManager.flush() + entityManager.clear() before acquiring
            // pessimistic locks. If we passed `savedUser` directly, it would
            // become detached after clear() and accountRepository.findByUser()
            // would return 0 accounts → totalNrwht = 0 → refund silently skipped.
            //
            // By passing the id (a primitive Long), NrwhtRefundService re-fetches
            // the user from the DB after clear(), getting a fresh managed entity.
            //
            // If the refund fails, REQUIRES_NEW means only the refund transaction
            // rolls back — the TIN update above is already committed.
            try {
                nrwhtRefundService.processRefundOnTinRegistration(savedUser.getId(), updatedBy);
            } catch (Exception e) {
                // Non-fatal: TIN update already committed. Teller can manually process refund.
                log.error("NRWHT auto-refund failed for user {} (TIN update saved OK): {}",
                        savedUser.getEmail(), e.getMessage(), e);
            }
        }

        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    /**
     * Handles deactivate user.
     * @param id the unique identifier of the target record.
     */
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setActive(false);
        userRepository.save(user);
        log.info("User {} deactivated", user.getEmail());
    }

    @Override
    @Transactional
    /**
     * Handles activate user.
     * @param id the unique identifier of the target record.
     */
    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setActive(true);
        userRepository.save(user);
        log.info("User {} activated", user.getEmail());
    }

    /**
     * Returns the email of the currently authenticated teller/admin from the
     * Spring Security context. Used as the "updatedBy" audit field.
     * Falls back to "system" if no authentication context is available.
     */
    private String resolveCurrentPrincipal() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not resolve security principal: {}", e.getMessage());
        }
        return "system";
    }
}
