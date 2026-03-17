package com.macroflow.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;

/**
 * Verifies Apple ID tokens against Apple's JWKS endpoint.
 *
 * Uses Nimbus JOSE+JWT to fetch Apple's public keys and validate the token
 * signature, expiry, and issuer. The JWKS is fetched lazily and cached by
 * the Nimbus RemoteJWKSet implementation.
 */
@Component
public class AppleTokenVerifier implements ProviderTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(AppleTokenVerifier.class);
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public AppleTokenVerifier() {
        try {
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(APPLE_JWKS_URL));
            JWSVerificationKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

            jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(keySelector);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise Apple JWKS verifier", e);
        }
    }

    @Override
    public String provider() {
        return "apple";
    }

    @Override
    public ProviderClaims verify(String idToken) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(idToken, null);

            // Validate issuer — Apple tokens must come from appleid.apple.com
            if (!APPLE_ISSUER.equals(claims.getIssuer())) {
                throw new ProviderVerificationException("Invalid Apple token issuer: " + claims.getIssuer());
            }

            String sub = claims.getSubject();
            String email = (String) claims.getClaim("email");
            return new ProviderClaims(sub, email);
        } catch (ProviderVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Apple token verification failed: {}", e.getMessage());
            throw new ProviderVerificationException("Invalid Apple ID token", e);
        }
    }
}
