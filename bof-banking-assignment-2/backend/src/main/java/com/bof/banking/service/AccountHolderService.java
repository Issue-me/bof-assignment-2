package com.bof.banking.service;

import com.bof.banking.dto.account.AccountHolderResponse;
import com.bof.banking.dto.account.AddAccountHolderRequest;

import java.util.List;

/**
 * Service interface for managing account holders.
 */
public interface AccountHolderService {
    
    /**
     * Add an account holder to an account.
     * 
     * @param accountId the account ID
     * @param request the account holder details
     * @param requestingUsername the username of the user making the request
     * @return the created account holder
     */
    AccountHolderResponse addAccountHolder(Long accountId, AddAccountHolderRequest request, String requestingUsername);
    
    /**
     * Remove an account holder from an account.
     * 
     * @param accountId the account ID
     * @param userId the user ID to remove
     * @param requestingUsername the username of the user making the request
     */
    void removeAccountHolder(Long accountId, Long userId, String requestingUsername);
    
    /**
     * Get all account holders for an account.
     * 
     * @param accountId the account ID
     * @param requestingUsername the username of the user making the request
     * @return list of account holders
     */
    List<AccountHolderResponse> getAccountHolders(Long accountId, String requestingUsername);
    
    /**
     * Check if a user has access to an account (is an account holder).
     * 
     * @param accountId the account ID
     * @param userId the user ID
     * @return true if the user is an account holder
     */
    boolean hasAccessToAccount(Long accountId, Long userId);
}
