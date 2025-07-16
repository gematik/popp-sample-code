# Release 1.0.2

- initial release

### Features

- Generate PoPP tokens using a standard PC/SC USB terminal or Konnektor and eHealth card terminal.
- Configuration of Konnektor connection possible

### Fixed 

- TLS connection to Konnektor with ECC is working

### Known Issues

- Service endpoints are derived only from EndpointTLS entries, not from unsecured Endpoint entries
- EventService.xsd references CardService with namespace http://ws.gematik.de/conn/CardService/v8.1 instead of v8.2.
- The card handle of the first card returned in the GetCards response is used, regardless of card type; this may result in a non-eGK card handle being used for StartCardSession.
