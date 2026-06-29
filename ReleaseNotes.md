<img align="right" width="250" height="47" src="Gematik_Logo_Flag_With_Background.png" /> <br />     

# Release Notes popp-sample-code

### Known Issues
- Standard-Kartenleser with Docker is not supported

## Release 2.5.2

### changed
- Replaced hardcoded error code strings with error codes defined in the gemSpec_PoPP_Service specification.
- The Zeta SDK client is now a singleton, and Zeta registration is performed only once. The registration data is stored using the SDK's built-in encrypted storage.
- Updated ZETA version to 1.2.0

### added
- Added support for selecting a specific card slot via ct-slot when multiple card slots are available in a terminal

## Release 2.5.1

### fixed
- contact(less)-virtual now uses correct SubCA CVC from card image used

## Release 2.5.0

### added
- Added support to switch virtual card images during runtime (via /token request)

## Release 2.4.1

### added
- Added support for selecting a specific card terminal via ct-id when multiple terminals are connected to a Konnektor
- Added endpoint (popp-client) for popp token verification: /token/verify

### changed
- Replaced static actorId value with value extracted from the zeta-user-info header
- Updated ZETA version to 1.0.1

## Release 2.4.0

### changed
- Replaced the former Smartcard-library based CVC and trusted-channel handling in PoPP client and server with OpenHealth-based `CvCertificate`, healthcard parsing, and crypto verification APIs
- Reworked trusted CVC directory loading and trusted-channel chain building on the server to use OpenHealth instead of `TrustCenter` and `SecureMessagingConverterSoftware`
- Switched the configured trusted-channel service identity validation to OpenHealth using the configured PKCS#8 key material directly
- Simplified server-side key handling by removing obsolete parser and factory layers around private keys and JCA key store access
- Added support for Contactless Virtual Card

### fixed
- Fixed contact-based and contactless CVC signature verification and nonce verification to use the same OpenHealth-based verification logic across scenarios
- Fixed parsing of `LIST PUBLIC KEY` and `EF.Version2` responses by using OpenHealth parsers instead of local TLV decoding

## Release 2.3.0

### added
- Added Well-Known endpoints for OpenID Federation and signed JWKS:
  - /.well-known/openid-federation (entity statement JWT)
  - /.well-known/signed-jwks (signed JWK set)
- URL for RISE PoPP Service is now public in README and application.yaml

### fixed
- PoPP server health check

## Release 2.2.0

### added
- Added updated trusted CVC identity material for current card and CVCA chains

### changed
- Updated ZETA version to 0.5.1
- Reworked contact-based and contactless scenario handling to use typed scenario and step definitions with centralized APDU resolution
- Simplified server scenario configuration by removing obsolete YAML imports and unused settings

### fixed
- Improved robustness of command building and exception handling around card APDU generation

## Release 2.1.6

### fixed
- Tests in module popp-server are working again

## Release 2.1.5

### changed

- Updated ZETA version to 0.4.2
- Removed static key id for signing PoPP token and JWKS endpoint, instead we calculate the correct key id now

## Release 2.1.4

### changed

- Introduce static key id for signing PoPP token and JWKS endpoint

## Release 2.1.3

## added
- Our PoPP-Service now has a JWKS endpoint

## changed
- We changed how to start our PoPP-Client and PoPP-Service, please look at the README if you have questions
  - This means you can now also start the PoPP-Client locally or with Docker
- Updated the invocations in the swagger UI

## fixed
- getCards gets now the correct EventServiceVersion

## Release 2.1.2

### changed
- ZETA integration
- You can now use PoPP-Service from this project directly or use the RISE PoPP-Service after some configuration changes (see README.md)

## Release 2.0.0

### changed 

- API change: The PoPP token is now returned in the REST response.  
- API break: To generate a PoPP token, use the following POST endpoint instead of GET:
  `POST http://localhost:8081/token`
  See `README.md` for the required request body.
- You no longer need to specify the SOAP services in `application.yaml`; the versions are now read from `connector.sds`.
- Virtual card images are now supported.  
  See `README.md` for details.

### fixed

- it is now possible to skip tests with -DskipTests=true
- Postgres service is starting via docker compose [Issue 13](https://github.com/gematik/popp-sample-code/issues/13)

## Release 1.0.3

### fixed

- supports both CardService versions 8.1 and 8.2 [Issue 2](https://github.com/gematik/popp-sample-code/issues/2)
- Filter GetCards by CardType [Issue 3](https://github.com/gematik/popp-sample-code/issues/3)
- use correct endpoints for CardService requests [Issue 4](https://github.com/gematik/popp-sample-code/issues/4)
- Context added for StartCardSession requests [Issue 5](https://github.com/gematik/popp-sample-code/issues/5)
- TLS with Konnektor now works correctly [Issue 6](https://github.com/gematik/popp-sample-code/issues/6)
- hostname validation can be configured [Issue 7](https://github.com/gematik/popp-sample-code/issues/7)
- sessionId is now used correctly for StopCardSession [Issue 8](https://github.com/gematik/popp-sample-code/issues/8)
- unit tests are now compatible with Windows
- use end entity certificates for hash generation
- Telematik-ID can be changed in server configuration
- responses for CardService now use the correct marshaller

## Release 1.0.2

- initial release

### added

- generate PoPP tokens using a standard PC/SC USB terminal or Konnektor and eHealth card terminal
- configuration of Konnektor connection possible

### fixed 

- TLS connection to Konnektor with ECC is working
