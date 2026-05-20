package com.bof.banking.service.impl;

import com.bof.banking.config.BankingProperties;
import com.bof.banking.dto.account.AccountResponseDto;
import com.bof.banking.dto.user.CreateCustomerRequest;
import com.bof.banking.dto.user.CustomerDetailResponse;
import com.bof.banking.dto.user.UpdateCustomerRequest;
import com.bof.banking.dto.user.UserResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.mapper.AccountMapper;
import com.bof.banking.model.Account;
import com.bof.banking.model.Role;
import com.bof.banking.model.User;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.CustomerManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of CustomerManagementService for managing customer profiles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerManagementServiceImpl implements CustomerManagementService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final BankingProperties bankingProperties;
    private final AccountMapper accountMapper;
    private final NrwhtRefundService nrwhtRefundService;

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns all customers data.
     * @return the matching results.
     */
    public List<UserResponse> getAllCustomers() {
        return userRepository.findByRole(Role.CUSTOMER).stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns customer detail data.
     * @param customerId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public CustomerDetailResponse getCustomerDetail(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        if (customer.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("User is not a customer");
        }

        List<Account> accounts = accountRepository.findByUserId(customerId);
        List<AccountResponseDto> accountDtos = accounts.stream()
                .map(accountMapper::toResponseDto)
                .collect(Collectors.toList());

        return CustomerDetailResponse.builder()
                .id(customer.getId())
                .customerId(customer.getCustomerId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .role(customer.getRole().name())
                .active(customer.isActive())
                .tinNumber(customer.getTinNumber())
                .nationalId(customer.getNationalId())
                .dateOfBirth(customer.getDateOfBirth())
                .address(customer.getAddress())
                .resident(customer.isResident())
                .createdAt(customer.getCreatedAt())
                .lastLogin(customer.getLastLogin())
                .accounts(accountDtos)
                .totalAccounts(accountDtos.size())
                .build();
    }

    @Override
    @Transactional
    /**
     * Creates customer data.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public UserResponse createCustomer(CreateCustomerRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already in use: " + request.getEmail());
        }

        String customerId = generateCustomerId();

        User customer = User.builder()
                .customerId(customerId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(Role.CUSTOMER)
                .isActive(true)
                .tinNumber(request.getTinNumber())
                .nationalId(request.getNationalId())
                .dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress())
                .isResident(request.isResident())
                .build();

        User saved = userRepository.save(customer);
        log.info("New customer created: {} - {} by admin/teller", customerId, saved.getEmail());

        return toUserResponse(saved);
    }

    @Override
    @Transactional
    /**
     * Updates customer values.
     * @param customerId the unique identifier of the target record.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public UserResponse updateCustomer(Long customerId, UpdateCustomerRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        boolean hadTinBefore = customer.getTinNumber() != null && !customer.getTinNumber().isBlank();
        boolean wasResidentBefore = customer.isResident();

        if (customer.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("User is not a customer");
        }

        // Update only provided fields
        if (request.getFirstName() != null) {
            customer.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            customer.setLastName(request.getLastName());
        }
        if (request.getEmail() != null && !request.getEmail().equals(customer.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email already in use: " + request.getEmail());
            }
            customer.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            customer.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getTinNumber() != null) {
            customer.setTinNumber(request.getTinNumber());
        }
        if (request.getNationalId() != null) {
            customer.setNationalId(request.getNationalId());
        }
        if (request.getDateOfBirth() != null) {
            customer.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getAddress() != null) {
            customer.setAddress(request.getAddress());
        }
        if (request.getIsResident() != null) {
            customer.setResident(request.getIsResident());
        }

        // boolean nowHasTin = request.getTinNumber() != null;
        // boolean wasResident = customer.isResident(); 

        User updated = userRepository.save(customer);

        userRepository.flush();

        log.info("Customer profile updated: {}", customer.getCustomerId());

        boolean nowHasTin = updated.getTinNumber() != null && !updated.getTinNumber().isBlank();
        boolean isResidentNow = updated.isResident();

        log.warn("NRWHT CHECK → hadTin={} nowHasTin={} wasResident={} nowResident={}",
                hadTinBefore, nowHasTin, wasResidentBefore, isResidentNow);

        if (!hadTinBefore && nowHasTin && isResidentNow) {
            log.warn("NRWHT REFUND TRIGGERED for customerId={}", customerId);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            /**
             * Hooks into lifecycle processing for CustomerManagementServiceImpl to keep entity state consistent.
             */
            public void afterCommit() {
                nrwhtRefundService.processRefundOnTinRegistration(
                    customerId,
                    "CUSTOMER_PROFILE_UPDATE"
                );
            }
            });
        }
        
        return toUserResponse(updated);
    }

    @Override
    @Transactional
    /**
     * Handles deactivate customer.
     * @param customerId the unique identifier of the target record.
     */
    public void deactivateCustomer(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        if (customer.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("User is not a customer");
        }

        customer.setActive(false);
        userRepository.save(customer);
        log.info("Customer deactivated: {}", customer.getCustomerId());
    }

    @Override
    @Transactional
    /**
     * Handles activate customer.
     * @param customerId the unique identifier of the target record.
     */
    public void activateCustomer(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));

        if (customer.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("User is not a customer");
        }

        customer.setActive(true);
        userRepository.save(customer);
        log.info("Customer activated: {}", customer.getCustomerId());
    }

    private String generateCustomerId() {
        String prefix = bankingProperties.getCustomerIdPrefix();
        long count = userRepository.count();
        return String.format("%s-%06d", prefix, count + 1);
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .customerId(user.getCustomerId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .active(user.isActive())
                .tinNumber(user.getTinNumber())
                .resident(user.isResident())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }
}
