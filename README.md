# PoPP Sample Implementation

## Overview

This project provides a sample implementation of the PoPP-Service and
PoPP-Client according to [gemSpec_PoPP_Service](https://gemspec.gematik.de/prereleases/Draft_PoPP_25_1/gemSpec_PoPP_Service_V1.0.0_CC2).
The [eGK-Hash-Datenbank](https://gemspec.gematik.de/prereleases/Draft_PoPP_25_1/gemSpec_PoPP_Service_V1.0.0_CC2/#6.2.1.9) is implemented as PostgresSQL database.

You can run the components either locally or via Docker.

## Building and running the project locally

## ⚙️ Prerequisites

- **Java JDK/JRE 21**
- **Docker**
- **eGK Testkarten** [if you need some, go to gematik Onlineshop](https://fachportal.gematik.de/gematik-onlineshop/testkarten?ai%5Baction%5D=detail&ai%5Bcontroller%5D=Catalog&ai%5Bd_name%5D=testkarte-egk-g2&ai%5Bd_pos%5D=1)  
- **Standard-Kartenleser** (PC/SC via USB) or
- **Konnektor and eHealth Kartenterminal**

> All keys and p12 stores contained in this repository are intentionally published to allow the project to run out of the box after cloning.

*Notes*

- Ensure ports '8081' (PoPP-Client), `5432` (eGK-Hash-Datenbank) and `8443` (PoPP-Service) are free.
- You can modify `docker-compose.yml` to fit your environment.

## Step-by-step guide

### Build

```bash
  ./mvnw clean install
```

Optionally without tests:

```bash
  ./mvnw clean install -Dmaven.test.skip=true
```

### Configuration

All described configuration options must be implemented in the `application-dev.yaml` of the PoPP-Client.

#### a) Standard-Kartenleser

"Standard-Kartenleser" is a PC/SC card reader, which is typically connected via USB.

By default, the PoPP-Client uses the first Standard-Kartenleser it detects. However, you can specify explicitly which Standard-Kartenleser to use.

```yaml
cardreader:
      name: "<card reader name>"
```

The name is case-sensitive but does not have to be complete, for example with "REINER SCT" the
Standard-Kartenleser named "REINER SCT cyberJack RFID standard 1" will be found.

#### b) Konnektor

**Optional: Certificates for TLS**

The PoPP-Client supports TLS connections using [ECC](https://gemspec.gematik.de/docs/gemILF/gemILF_PS/latest/#A_17094-01) for communication with your Konnektor. To enable and configure this, follow the steps below:

1. Enable TLS by setting:

   ```yml
   connector:
     secure:
       enable: true
   ```
* If your Konnektor does not have a resolvable domain name, disable hostname validation:
  
  ```yml
  connector:
    secure:
      hostname-validation: false
  ```
  
* Upload your client certificate (e.g., `keystore.p12`) to the Konnektor client configuration.
* Retrieve the Konnektor certificate (replace `<KONNEKTOR_IP>` and `<PORT>` with your specific Konnektor address):

  ```
  openssl s_client -showcerts -connect <Konnektor_IP>:<PORT>
  ```
  
  Depending on the Konnektor type, you might add a curves parameter to the command, e.g.:

  ```
  openssl s_client -showcerts -connect <Konnektor_IP>:<PORT> -curves brainpoolP256r1
  ```

  or a cipher parameter, e.g.:

  ```
  openssl s_client -showcerts -connect <Konnektor_IP>:<PORT> -cipher ECDHE-ECDSA-AES128-GCM-SHA256
  ```

* Import the Konnektor certificate including the whole trust chain into your truststore (e.g., truststore.p12):

  ```
  keytool -import -alias connector-server -file server-cert.pem -keystore truststore.p12
  ```
  
* Note: The `connector.secure.keystore`, `connector.secure.truststore`, and their respective passwords are used only
  for communication with the Konnektor. This is distinct from the TLS configuration used for communication with 
  the PoPP server.

**Address and context**

Configure your Konnektor address and context:

```yaml
connector:
  end-point-url: <ip address and port of event-service endpoint of Konnektor>
  terminal-configuration:
  log-ws: <if SOAP messages should be logged>
  secure:
    enable: <If TLS should be used>
    hostname-validation: <The Hostname of the Konnektor should be validated>
    keystore: <Keystore with the client certificate>
    keystore-password: <Password of the keystore>
    trust-all: <If all certificates should be trusted, only for testing purposes>
    truststore: <Truststore with the Konnektor certificate and its trust chain>
    truststore-password: <Password of the truststore>
  context:
    clientSystemId: <ClientSystemId for Konnektor Context>
    workplaceId: <WorkplaceId for Konnektor Context>
    mandantId: <MandantId for Konnektor Context>
  soap-services:
    get-cards: <GetCards soap service endpoint>
    start-card-session: <StartCardSession soap service endpoint>
    stop-card-session: <StopCardSession soap service endpoint>
    secure-send-apdu: <SecureSendApdu soap service endpoint>
```
Example:

```yaml
connector:
  end-point-url: "http://127.0.0.1"
  terminal-configuration:
  log-ws: true
  secure:
    enable: false
    hostname-validation: true
    keystore: keystore.p12
    keystore-password: changeit
    trust-all: false
    truststore: truststore.p12
    truststore-password: changeit
  context:
    clientSystemId: "ClientID1"
    workplaceId: "Workplace1"
    mandantId: "Mandant1"
  soap-services:
    get-cards: "http://ws.gematik.de/conn/EventService/v7.2#GetCards"
    start-card-session: "http://ws.gematik.de/conn/CardService/v8.2#StartCardSession"
    stop-card-session: "http://ws.gematik.de/conn/CardService/v8.2#StopCardSession"
    secure-send-apdu: "http://ws.gematik.de/conn/CardService/v8.2#SecureSendAPDU"
```

**Supported Konnektor functions**

- `StartCardSession` - see [StardCardSession in gemSpec_Kon](https://gemspec.gematik.de/docs/gemSpec/gemSpec_Kon/latest/#4.1.5.5.7)
- `SecureSendApdu`   - see [SecureSendApdu in gemSpec_Kon](https://gemspec.gematik.de/docs/gemSpec/gemSpec_Kon/latest/#4.1.5.5.8)
- `StopCardSession`  - see [StopCardSession in gemSpec_Kon](https://gemspec.gematik.de/docs/gemSpec/gemSpec_Kon/latest/#4.1.5.5.9)
- `GetCards`         - see [GetCards in gemSpec_Kon](https://gemspec.gematik.de/docs/gemSpec/gemSpec_Kon/latest/#4.1.6.5.2)
        -- If GetCards finds more than one eGK the first one is used.

## Execution

### Start the eGK-Hash-Datenbank

Before running the PoPP-Service, you need to start the eGK-Hash-Datenbank.

```bash
  cd popp-server/docker && docker compose --file postgres-dev.yaml up
```

For powershell use:

```bash
  cd popp-server/docker; docker compose --file postgres-dev.yaml up
```

To stop and remove the containers, run the following command:

```bash
  cd docker && docker compose down
```

For powershell use:

```bash
  cd docker; docker compose down)
```

### Start the PoPP-Service

```bash
  ./mvnw -pl popp-server spring-boot:run
```

### Start the PoPP-Client

```bash
  ./mvnw -pl popp-client spring-boot:run
```

For Linux with additional argument:

```bash
  ./mvnw -pl popp-client spring-boot:run -Dspring-boot.run.jvmArguments="-Dsun.security.smartcardio.library=/lib/x86_64-linux-gnu/libpcsclite.so.1"
```

#### Alternatively: Docker usage (Service & Database)

```bash
  cd docker && docker compose up
```

The client must be started locally!

### Executing the tests

To execute the tests for the whole project, run the following command:

```bash
  ./mvnw clean test
```

To execute also the integration tests, run the following command:

```bash
  ./mvnw clean verify
```

To execute the tests for a specific module, use the `-pl` option to specify the module name. For example, to run the tests for the `popp-client` module, run:

```bash
  ./mvnw -pl popp-client clean test
```

## Generate a PoPP-Token

The client provides the following endpoint to generate a PoPP-Token:

`http://localhost:8081/token/{communication-type}?clientsessionid={client-session-id} `

The request parameter `clientsessionid` is optional. If set, the `clientsessionid` will overwrite the Konnektor `clientsessionid` from `StartCardSession`

The communication type must be one of the following:

- `contact-standard`
  - use contact-based interface from Standard-Kartenleser
- `contactless-standard`
  - use contactless interface from Standard-Kartenleser
- `contact-connector`
  - use contact-based interface from eHealth-Kartenterminal via Konnektor
  - *Note: if the client was started with mock configuration (see above) the scenario is simulated without any terminal and just with static APDUs*
- `contactless-connector`
  - use contactless interface from eHealth-Kartenterminal via Konnektor
- `contact-connector-via-standard-terminal`
  - generate sample messages for Konnektor via contact-based interface from Standard-Kartenleser

### Example usage

To generate a PoPP-Token with `contact-standard` option, open the following URL in your browser:
[http://localhost:8081/token/contact-standard](http://localhost:8081/token/contact-standard)

Alternatively, you can use this `curl` command in the terminal:

```bash
  curl http://localhost:8081/token/contact-standard
```

To view the generated PoPP-Token, check the console output of the client. 

For PoPP-Token claims see [api-popp](https://github.com/gematik/api-popp/blob/main/src/openapi/I_PoPP_Token_Generation.yaml).

## Sequence diagrams
### PoPP-Token with Standard-Kartenleser
![PoPP-Token with Standard-Kartenleser](images/PoPP_Token_Standard_Kartenleser.drawio.svg)

### PoPP-Token with Konnektor
![PoPP-Token with Standard-Kartenleser](images/PoPP_Token_Konnektor.drawio.svg)

### PoPP-Token with Standard-Kartenleser instead of Konnektor for generating sample messages
![PoPP-Token with Standard-Kartenleser](images/PoPP_Beispielnachrichten_Konnektor.drawio.svg)

## License

Copyright 2025-2025 gematik GmbH

Apache License, Version 2.0

See the [LICENSE](./LICENSE) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
    3. The software is the result of research and development activities, therefore not necessarily quality assured and without the character of a liable product. For this reason, gematik does not provide any support or other user assistance (unless otherwise stated in individual cases and without justification of a legal obligation). Furthermore, there is no claim to further development and adaptation of the results to a more current state of the art.
3. Gematik may remove published results temporarily or permanently from the place of publication at any time without prior notice or justification.
4. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.
