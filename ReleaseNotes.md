<img align="right" width="250" height="47" src="Gematik_Logo_Flag_With_Background.png" /> <br />     

# Release Notes popp-sample-code

## Release 2.0.0

### changed 

- API change: The PoPP token is now returned in the REST response.  
- API break: To generate a PoPP token, use the following POST endpoint instead of GET:
  `POST http://localhost:8081/token`
  See `README.md` for the required request body.
- You no longer need to specify the SOAP services in `application.yaml`; the versions are now read from `connector.sds`.
- Virtual card images are now supported.  
  See `README.md` for details.
- 


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