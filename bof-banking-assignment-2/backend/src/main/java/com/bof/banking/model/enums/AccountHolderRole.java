package com.bof.banking.model.enums;

/**
 * Enum representing the role of an account holder.
 */
public enum AccountHolderRole {
    /**
     * Primary account holder with full permissions
     */
    PRIMARY,
    
    /**
     * Joint/secondary account holder with full access
     */
    JOINT,
    
    /**
     * Authorized user with limited permissions (view and transactions only)
     */
    AUTHORIZED
}
