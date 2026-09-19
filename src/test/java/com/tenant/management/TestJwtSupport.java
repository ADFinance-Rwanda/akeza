package com.tenant.management;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class TestJwtSupport {

    public static final String ISSUER = "http://auth.test/realms/taskmgr";
    private static final RSAKey RSA_KEY;
    private static final RSAKey OTHER_KEY;

    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).keyID("taskmgr-test").generate();
            OTHER_KEY = new RSAKeyGenerator(2048).keyID("other").generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private TestJwtSupport() {
    }

    public static JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String alice() {
        return token("alice-sub", "alice@example.com", "Alice", "Admin", Instant.now().plusSeconds(3600), RSA_KEY, ISSUER);
    }

    public static String bob() {
        return token("bob-sub", "bob@example.com", "Bob", "Builder", Instant.now().plusSeconds(3600), RSA_KEY, ISSUER);
    }

    public static String carol() {
        return token("carol-sub", "carol@example.com", "Carol", "Member", Instant.now().plusSeconds(3600), RSA_KEY, ISSUER);
    }

    public static String dave() {
        return token("dave-sub", "dave@example.com", "Dave", "Member", Instant.now().plusSeconds(3600), RSA_KEY, ISSUER, List.of("user"));
    }

    public static String erin() {
        return token("erin-sub", "erin@example.com", "Erin", "Super", Instant.now().plusSeconds(3600), RSA_KEY, ISSUER, List.of("user", "SUPER_ADMIN"));
    }

    public static String expired() {
        return token("alice-sub", "alice@example.com", "Alice", "Admin", Instant.now().minusSeconds(3600), RSA_KEY, ISSUER, List.of("user"));
    }

    public static String wrongIssuer() {
        return token("alice-sub", "alice@example.com", "Alice", "Admin", Instant.now().plusSeconds(3600), RSA_KEY, "http://evil.test/realms/taskmgr", List.of("user"));
    }

    public static String wrongSignature() {
        return token("alice-sub", "alice@example.com", "Alice", "Admin", Instant.now().plusSeconds(3600), OTHER_KEY, ISSUER, List.of("user"));
    }

    private static String token(String sub, String email, String given, String family, Instant exp, RSAKey key, String issuer) {
        return token(sub, email, given, family, exp, key, issuer, List.of("user"));
    }

    private static String token(String sub, String email, String given, String family, Instant exp, RSAKey key, String issuer, List<String> roles) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sub)
                    .issuer(issuer)
                    .issueTime(Date.from(Instant.now().minusSeconds(5)))
                    .expirationTime(Date.from(exp))
                    .claim("email", email)
                    .claim("given_name", given)
                    .claim("family_name", family)
                    .claim("realm_access", Map.of("roles", roles))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
