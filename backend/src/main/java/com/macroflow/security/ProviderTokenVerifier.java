package com.macroflow.security;

/**
 * Abstraction over Apple and Google ID token verification.
 *
 * Each implementation verifies the provider's ID token against the provider's
 * JWKS endpoint and extracts the {@code sub} and {@code email} claims.
 * Implementations are selected by the {@code provider} field in the token request.
 */
public interface ProviderTokenVerifier {

    /**
     * Verify the provider ID token and return the extracted claims.
     *
     * @param idToken the raw ID token string from the mobile client
     * @return verified claims containing at minimum {@code sub} and {@code email}
     * @throws ProviderVerificationException if the token is invalid, expired, or cannot be verified
     */
    ProviderClaims verify(String idToken);

    /**
     * The provider name this verifier handles (e.g. {@code "google"}, {@code "apple"}).
     */
    String provider();

    /** Claims extracted from a successfully verified provider ID token. */
    record ProviderClaims(String sub, String email) {}

    /** Thrown when provider token verification fails for any reason. */
    class ProviderVerificationException extends RuntimeException {
        public ProviderVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
        public ProviderVerificationException(String message) {
            super(message);
        }
    }
}
