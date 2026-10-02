/*
 * Copyright 2022-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sleeper.api.auth.testutils;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/**
 * Mints tokens for tests, signed with a key generated for the test rather than a key checked into the repository.
 */
public class TestJwts {

    private final RSAKey key;

    private TestJwts(RSAKey key) {
        this.key = key;
    }

    /**
     * Creates an issuer with a freshly generated signing key.
     *
     * @return                      the issuer
     * @throws IllegalStateException if a key could not be generated
     */
    public static TestJwts issuer() {
        try {
            return new TestJwts(new RSAKeyGenerator(2048).keyID("test-key").generate());
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate a signing key for the test", e);
        }
    }

    /**
     * Mints a token signed by this issuer.
     *
     * @param  issuer  the issuer to claim the token came from
     * @param  subject the subject to identify the client by
     * @return         the token
     */
    public String token(String issuer, String subject) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    new JWTClaimsSet.Builder().issuer(issuer).subject(subject).build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Could not mint a token for the test", e);
        }
    }

    /**
     * Mints a token with no signature at all.
     *
     * @param  issuer the issuer to claim the token came from
     * @return        the token
     */
    public static String unsignedToken(String issuer) {
        return new PlainJWT(new JWTClaimsSet.Builder().issuer(issuer).subject("some-subject").build()).serialize();
    }

}
