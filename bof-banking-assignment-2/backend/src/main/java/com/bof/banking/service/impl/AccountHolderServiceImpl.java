package com.bof.banking.service.impl;

import com.bof.banking.dto.account.AccountHolderResponse;
import com.bof.banking.dto.account.AddAccountHolderRequest;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.exception.UnauthorizedException;
import com.bof.banking.model.Account;
import com.bof.banking.model.AccountHolder;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.AccountHolderRole;
import com.bof.banking.repository.AccountHolderRepository;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.AccountHolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AccountHolderService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountHolderServiceImpl implements AccountHolderService {

    private final AccountHolderRepository accountHolderRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    /**
     * Creates account holder data.
     * @param accountId the unique identifier of the target record.
     * @param request the request payload.
     * @param requestingUsername the request payload.
     * @return the result of the operation.
     */
    public AccountHolderResponse addAccountHolder(Long accountId, AddAccountHolderRequest request, String requestingUsername) {
        // Get requesting user
        User requestingUser = userRepository.findByEmail(requestingUsername)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requestingUsername));

        // Get account
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        // Verify requesting user has permission (must be primary or joint account holder)
        AccountHolder requestingHolder = accountHolderRepository.findByAccountIdAndUserId(accountId, requestingUser.getId())
            .orElseThrow(() -> new UnauthorizedException("You do not have permission to add account holders"));

        if (requestingHolder.getRole() == AccountHolderRole.AUTHORIZED) {
            throw new UnauthorizedException("Authorized users cannot add account holders");
        }

        // Find user to add
        User userToAdd = userRepository.findByEmail(request.getUserEmail())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserEmail()));

        // Check if user is already an account holder
        if (accountHolderRepository.existsByAccountIdAndUserId(accountId, userToAdd.getId())) {
            throw new IllegalStateException("User is already an account holder");
        }

        // Only primary account holders can add other primary holders
        if (request.getRole() == AccountHolderRole.PRIMARY && requestingHolder.getRole() != AccountHolderRole.PRIMARY) {
            throw new UnauthorizedException("Only primary account holders can add other primary holders");
        }

        // Create account holder
        AccountHolder accountHolder = AccountHolder.builder()
            .account(account)
            .user(userToAdd)
            .role(request.getRole())
            .addedByUserId(requestingUser.getId())
            .build();

        accountHolder = accountHolderRepository.save(accountHolder);
        log.info("Added account holder: user {} to account {}", userToAdd.getEmail(), accountId);

        return mapToResponse(accountHolder);
    }

    @Override
    @Transactional
    /**
     * Removes account holder data.
     * @param accountId the unique identifier of the target record.
     * @param userId the unique identifier of the target record.
     * @param requestingUsername the request payload.
     */
    public void removeAccountHolder(Long accountId, Long userId, String requestingUsername) {
        // Get requesting user
        User requestingUser = userRepository.findByEmail(requestingUsername)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requestingUsername));

        // Get account
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        // Verify requesting user has permission
        AccountHolder requestingHolder = accountHolderRepository.findByAccountIdAndUserId(accountId, requestingUser.getId())
            .orElseThrow(() -> new UnauthorizedException("You do not have permission to remove account holders"));

        if (requestingHolder.getRole() == AccountHolderRole.AUTHORIZED) {
            throw new UnauthorizedException("Authorized users cannot remove account holders");
        }

        // Get account holder to remove
        AccountHolder holderToRemove = accountHolderRepository.findByAccountIdAndUserId(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Account holder not found"));

        // Cannot remove primary account holders unless you are also primary
        if (holderToRemove.getRole() == AccountHolderRole.PRIMARY && requestingHolder.getRole() != AccountHolderRole.PRIMARY) {
            throw new UnauthorizedException("Only primary account holders can remove other primary holders");
        }

        // Check if this is the last primary holder
        List<AccountHolder> primaryHolders = accountHolderRepository.findByAccountId(accountId).stream()
            .filter(ah -> ah.getRole() == AccountHolderRole.PRIMARY)
            .collect(Collectors.toList());

        if (primaryHolders.size() == 1 && holderToRemove.getRole() == AccountHolderRole.PRIMARY) {
            throw new IllegalStateException("Cannot remove the last primary account holder");
        }

        accountHolderRepository.deleteByAccountIdAndUserId(accountId, userId);
        log.info("Removed account holder: user {} from account {}", userId, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns account holders data.
     * @param accountId the unique identifier of the target record.
     * @param requestingUsername the request payload.
     * @return the matching results.
     */
    public List<AccountHolderResponse> getAccountHolders(Long accountId, String requestingUsername) {
        // Get requesting user
        User requestingUser = userRepository.findByEmail(requestingUsername)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requestingUsername));

        // Verify requesting user has access to the account
        if (!accountHolderRepository.existsByAccountIdAndUserId(accountId, requestingUser.getId())) {
            throw new UnauthorizedException("You do not have access to this account");
        }

        List<AccountHolder> holders = accountHolderRepository.findByAccountId(accountId);
        return holders.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Checks whether access to account is valid.
     * @param accountId the unique identifier of the target record.
     * @param userId the unique identifier of the target record.
     * @return true if the condition is met; otherwise false.
     */
    public boolean hasAccessToAccount(Long accountId, Long userId) {
        return accountHolderRepository.existsByAccountIdAndUserId(accountId, userId);
    }

    private AccountHolderResponse mapToResponse(AccountHolder holder) {
        User user = holder.getUser();
        return AccountHolderResponse.builder()
            .id(holder.getId())
            .accountId(holder.getAccount().getId())
            .userId(user.getId())
            .userEmail(user.getEmail())
            .userFullName(user.getFirstName() + " " + user.getLastName())
            .role(holder.getRole())
            .addedAt(holder.getAddedAt())
            .addedByUserId(holder.getAddedByUserId())
            .build();
    }
}
