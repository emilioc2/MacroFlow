package com.macroflow.security;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies Google ID tokens using the Google Auth Library.
 *
 * TokenVerifier.verify() returns a JsonWebSignature (not IdToken) — we extract
 * sub and email from its payload. The library fetches and caches Google's JWKS
 * automatically; no API key is required for ID token verification.
 */
@Component
public class GoogleTokenVerifier implements ProviderTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final TokenVerifier verifier;

    public GoogleTokenVerifier() {
        // No audience restriction here — for production, restrict to your Google OAuth client ID
        // via TokenVerifier.newBuilder().setAudience(clientId).build()
        this.verifier = TokenVerifier.newBuilder().build();
    }

    @Override
    public String provider() {
        return "google";
    }

    @Override
    public ProviderClaims verify(String idToken) {
        try {
            // verify() returns JsonWebSignature; getPayload() gives access to standard JWT claims
            JsonWebSignature jws = verifier.verify(idToken);
            String sub = jws.getPayload().getSubject();
            String email = (String) jws.getPayload().get("email");
            return new ProviderClaims(sub, email);
        } catch (TokenVerifier.VerificationException e) {
            log.debug("Google token verification failed: {}", e.getMessage());
            throw new ProviderVerificationException("Invalid Google ID token", e);
        }
    }
}
