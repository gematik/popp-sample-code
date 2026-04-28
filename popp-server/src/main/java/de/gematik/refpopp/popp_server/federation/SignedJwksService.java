/*
 * Copyright (Date see Readme), gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.refpopp.popp_server.federation;

import de.gematik.refpopp.popp_server.security.jwk.JwkKidGenerator;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SignedJwksService {

  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private final JwkKidGenerator jwkKidGenerator;
  private final FederationProperties federationProperties;
  private final KeyStore poppKeyStore;
  private final KeyStore federationKeyStore;
  private final String federationKeyStorePassword;

  public SignedJwksService(
      JwkKidGenerator jwkKidGenerator,
      FederationProperties federationProperties,
      @Qualifier("poppKeyStore") KeyStore poppKeyStore,
      @Qualifier("federationKeyStore") KeyStore federationKeyStore,
      @Value("${certificates.federation-keystore-password}") String federationKeyStorePassword) {
    this.jwkKidGenerator = jwkKidGenerator;
    this.federationProperties = federationProperties;
    this.poppKeyStore = poppKeyStore;
    this.federationKeyStore = federationKeyStore;
    this.federationKeyStorePassword = federationKeyStorePassword;
  }

  public String create() {
    try {
      var entitySigningPublicKey =
          (ECPublicKey)
              federationKeyStore
                  .getCertificate(federationProperties.getEntitySigningAlias())
                  .getPublicKey();
      var entitySigningKid = jwkKidGenerator.generate(entitySigningPublicKey);

      var poppTokenCertificate =
          poppKeyStore.getCertificate(federationProperties.getPoppTokenSigningAlias());
      var poppTokenPublicKey = (ECPublicKey) poppTokenCertificate.getPublicKey();
      var poppTokenKid = jwkKidGenerator.generate(poppTokenPublicKey);

      Map<String, Object> jwksPayload =
          Map.of(
              "keys",
              List.of(toPublicEcJwk(poppTokenPublicKey, poppTokenKid, poppTokenCertificate)));

      var entitySigningPrivateKey =
          (PrivateKey)
              federationKeyStore.getKey(
                  federationProperties.getEntitySigningAlias(),
                  federationKeyStorePassword.toCharArray());

      var jws = new JsonWebSignature();
      jws.setPayload(JsonUtil.toJson(jwksPayload));
      jws.setKey(entitySigningPrivateKey);
      jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256);
      jws.setHeader("typ", "jwk-set+jwt");
      jws.setHeader("kid", entitySigningKid);

      return jws.getCompactSerialization();
    } catch (KeyStoreException
        | UnrecoverableKeyException
        | NoSuchAlgorithmException
        | JoseException
        | CertificateEncodingException e) {
      throw new SignedJwksCreationException("Failed to create signed JWKS", e);
    }
  }

  private Map<String, Object> toPublicEcJwk(
      ECPublicKey publicKey, String kid, Certificate certificate)
      throws CertificateEncodingException {
    return Map.of(
        "kty",
        "EC",
        "crv",
        "P-256",
        "x",
        base64UrlUInt(publicKey.getW().getAffineX()),
        "y",
        base64UrlUInt(publicKey.getW().getAffineY()),
        "kid",
        kid,
        "alg",
        "ES256",
        "use",
        "sig",
        "x5c",
        List.of(toBase64Der(certificate)));
  }

  private String toBase64Der(Certificate certificate) throws CertificateEncodingException {
    return Base64.getEncoder().encodeToString(certificate.getEncoded());
  }

  private String base64UrlUInt(BigInteger value) {
    byte[] bytes = EcKeyCoordinateEncoder.toUnsignedFixedLength(value);
    return BASE64_URL_ENCODER.encodeToString(bytes);
  }
}
