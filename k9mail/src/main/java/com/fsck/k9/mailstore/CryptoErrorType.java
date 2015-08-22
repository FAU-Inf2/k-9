package com.fsck.k9.mailstore;

public enum CryptoErrorType {
    NONE,
    CRYPTO_API_RETURNED_ERROR,
    SIGNED_BUT_INCOMPLETE,
    ENCRYPTED_BUT_INCOMPLETE,
    CLIENT_SIDE_ERROR,
    GENERIC_ERROR,
    API_VERSION_MISMATCH,
    NO_OR_WRONG_PASSPHRASE,
    NO_USER_ID
}