# PoPP Sample Implementation

## Overview

This project provides a sample implementation of the PoPP-Service and
PoPP-Client according to [gemSpec_PoPP_Service](https://gemspec.gematik.de/prereleases/Draft_PoPP_25_1/gemSpec_PoPP_Service_V1.0.0_CC2).
The [eGK-Hash-Datenbank](https://gemspec.gematik.de/prereleases/Draft_PoPP_25_1/gemSpec_PoPP_Service_V1.0.0_CC2/#6.2.1.9) is implemented as PostgresSQL database.

- Supported startup modes:
  - Run the complete local stack via Docker with profile [`dev-local`](#quick-start-docker-full-profile--recommended-for-testing-with-the-virtual-card).
  - Run the PoPP-Client locally and all other components via Docker with profile [`dev-local`](#start-the-popp-client-locally-and-connect-to-the-dockerized-zeta-popp-server-and-egk-hash-datenbank).
  - Run the PoPP-Client against a RISE PoPP-Server without an additional Spring profile using [`application.yaml`](#start-the-popp-client-against-a-rise-popp-server).

The PoPP-Client uses the following profile matrix:

| Target group       | Spring profile | Config file | Default PoPP-Server URL | Notes |
|--------------------|---|---|-------------------------|---|
| RISE PoPP-Service  | none | `application.yaml` | Obtain the endpoint via the [gematik Anfrageportal](https://service.gematik.de/servicedesk/customer/portal/37) | Access requires allow-listing. |
| local PoPP-Service | `dev-local` | `application-dev-local.yaml` | `wss://popp-zeta-ingress:443/ws` | Used for the local Docker/ZETA stack. |

## Building and running the project locally

## ⚙️ Prerequisites

- **Java JDK/JRE 21**
- **Docker**
- **eGK Testkarten** [if you need some, go to gematik Onlineshop](https://fachportal.gematik.de/gematik-onlineshop/testkarten?ai%5Baction%5D=detail&ai%5Bcontroller%5D=Catalog&ai%5Bd_name%5D=testkarte-egk-g2&ai%5Bd_pos%5D=1)  
- **Standard-Kartenleser** (PC/SC via USB) or
- **Konnektor and eHealth Kartenterminal**

> The keys and p12 stores contained in this repository are intentionally published to allow the project to run out of the box after cloning.

> An exception is the SMC-B certificate and key pair. Please request a test SMC-B at [gematik Anfrageportal](https://service.gematik.de/servicedesk/customer/portal/37).

> Please place them in the docker/zeta/smcb-private folder as follows: 
> - smcb_private.p12 (p12 file with AUT_E256_X509 certificate)
> - smcb_private.alias.txt (alias of the smcb certificate in the p12 file, default is alias)
> - smcb_private.pw.txt (password of the p12 file).

*Notes*

- Ensure ports '8081' (PoPP-Client), `5432` (eGK-Hash-Datenbank) and `8443` (PoPP-Service) are free.
- You can modify `compose.yaml` to fit your environment.

## Step-by-step guide

### Build

```bash
  ./mvnw clean install
```

Optionally without tests:

```bash
  ./mvnw clean install -Dmaven.test.skip=true
```

or 

```bash
  ./mvnw clean install -DskipTests=true
```

### Configuration

Common overrides are available via environment variables such as
`CONNECTOR_END_POINT_URL`, `ZETA_AUTHENTICATION_SMB_KEYFILE` and `POPP_BASEDIR`.
The default value `docker/zeta/smcb-private/smcb_private.p12` is resolved against the current
working directory and, if needed, against its parent directories. This allows the same default
to work both from the repository root and from module directories such as `popp-client`.
If `POPP_BASEDIR` is set, relative paths are resolved against that directory first. This is
useful for Jenkins or external deployments where the repository layout is known but the start
directory may vary.
You can still set `ZETA_AUTHENTICATION_SMB_KEYFILE` explicitly; use an absolute path if in doubt,
for example
`/path/to/repo/docker/zeta/smcb-private/smcb_private.p12`.
The Docker Compose setup overrides that path to `/app/smcb_private.p12`.

Example for CI:

```bash
export POPP_BASEDIR="$WORKSPACE"
```

#### a) Standard-Kartenleser

"Standard-Kartenleser" is a PC/SC card reader, which is typically connected via USB.

By default, the PoPP-Client uses the first Standard-Kartenleser it detects. However, you can specify explicitly which Standard-Kartenleser to use.

```yaml
card-reader:
  name: "<card reader name>"
```

The name is case-sensitive but does not have to be complete, for example with "REINER SCT" the
Standard-Kartenleser named "REINER SCT cyberJack RFID standard 1" will be found.
However, the Standard-Kartenleser must be uniquely identifiable by the abbreviated name. In some cases
of dual-interface readers you must specify the full name to make sure that the right interface is used.


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
```

**Supported Konnektor functions**

- `StartCardSession` - see [StardCardSession in gemSpec_Kon](https://gemspec.gematik.de/docs/gemSpec/gemSpec_Kon/latest/#4.1.5.5.7)
- `SecureSendApdu`   - see [SecureSendApdu in gemSpec_Kon](https://gemspec.gematik.de/docs/gemSpec/gemSpec_Kon/latest/#4.1.5.5.8)
- `StopCardSession`  - see [StopCardSession in gemSpec_Kon](https://gemspec.gematik.de/docs/gemSpec/gemSpec_Kon/latest/#4.1.5.5.9)
- `GetCards`         - see [GetCards in gemSpec_Kon](https://gemspec.gematik.de/docs/gemSpec/gemSpec_Kon/latest/#4.1.6.5.2)
        -- If GetCards finds more than one eGK the first one is used.

#### c) Virtual Card

If you want to use a virtual card instead a card reader or Konnektor you can configure it as follows.
This allows testing without any card-related hardware.
The card data will be read from the XML image file specified.

```yaml
virtual-card:
  image-file: <XML image file>
```

## Execution

### Quick start (Docker, full profile – recommended for testing with the virtual card)


For a quick **out-of-the-box setup** (including **PoPP-Client**, **PoPP-Service**, and the **eGK-Hash-Datenbank**),
this project provides a Docker Compose **`full` profile**.

This mode is especially useful for:

- testing the **virtual card**
- running the complete PoPP stack without card readers or a Konnektor

#### 1. Build Docker images via Maven

Docker images are built as part of the Maven build.  
Make sure Docker image creation is **enabled**:

```bash
./mvnw clean install -Dskip.dockerbuild=false
```
This step builds the Docker images `local/popp/popp-server:<version>` and `local/popp/popp-client:<version>`, as well as
`local/popp/popp-server:latest` and `local/popp/popp-client:latest`. The latter is the default version in the compose.yaml.

#### 2. Start the full stack via Docker Compose

```bash
docker compose -f docker/compose.yaml --profile full up
```

The compose setup starts the client with `SPRING_PROFILES_ACTIVE=dev-local`.

This starts:

- PoPP-Server
- eGK-Hash-Datenbank (PostgreSQL)
- PoPP-Client (without card reader/Konnektor, using virtual card)

#### 3. Verify startup

Once all containers are running, the following endpoints are available:

- PoPP-Client Swagger UI: <http://localhost:8081/swagger-ui.html>

#### 4. Testing with a virtual card

The full Docker profile is intended to be used together with the virtual card configuration. You can test it via the 
Swagger-Ui with the `/token` endpoint as described below with the communication type `contact-virtual`.

This allows testing PoPP flows without any card-related hardware.

#### 5. Systematic test example: restart the full local Docker stack and verify virtual card flow

This test verifies the fully dockerized setup, including the dockerized PoPP-Client.

Stop the complete local stack first:

```bash
docker compose -f docker/compose.yaml --profile full down --remove-orphans
```

Start the complete local stack again:

```bash
docker compose -f docker/compose.yaml --profile full up -d
```

Check the container status:

```bash
docker compose -f docker/compose.yaml --profile full ps
```

The PoPP-Client should be up and healthy on port `8081`.

Run the virtual card token flow:

```bash
curl -H 'Content-Type: application/json' \
  -d '{"communicationType":"contact-virtual"}' \
  http://localhost:8081/token
```

Expected result:

```json
{
  "status": "OK",
  "token": "<PoPP token>"
}
```

If you want to execute the same check from inside the client container, use:

```bash
docker exec popp-client curl -H 'Content-Type: application/json' \
  -d '{"communicationType":"contact-virtual"}' \
  http://localhost:8081/token
```

### Start the PoPP-Client locally and connect to the Dockerized Zeta, PoPP-Server and eGK-Hash-Datenbank

This mode is especially useful for testing the complete PoPP stack with card readers or a Konnektor.
Use the `dev-local` profile for the client.

*For ZETA:*

- Ensure you have the following entry in your "hosts" file (e.g. in Windows under C:\Windows\System32\drivers\etc):

```bash
  127.0.0.1 popp-zeta-ingress
```

- The default local profile expects `popp-zeta-ingress` on port `443`.
- The `popp-zeta-ingress` host entry is mandatory for local client starts. The client uses that
  hostname both for the WebSocket endpoint and for the ZETA discovery and authorization endpoints.
- If you change the published port in `docker/compose.yaml`, override the URL with
  `POPP_SERVER_URL=wss://popp-zeta-ingress:<your_port>/ws`.

#### 1. Build Docker images via Maven as above

#### 2. Start all components except the PoPP-Client via Docker Compose

```bash
docker compose -f docker/compose.yaml up
```

This starts:

- Zeta
- PoPP-Server
- eGK-Hash-Datenbank (PostgreSQL)

#### 3. Start the PoPP-Client locally

For Maven:

```bash
./mvnw -pl popp-client spring-boot:run -Dspring-boot.run.profiles=dev-local
```

For an IDE start:

- activate profile `dev-local`

#### 4. Verify startup as above

### Start the PoPP-Client against a RISE PoPP-Server

#### Prerequisites:

Connecting to the RISE PoPP Server only works if you’ve been granted access. To get onto the allowlist, please visit the [gematik Anfrageportal](https://service.gematik.de/servicedesk/customer/portal/37).
You will get the correct endpoint with your request.

#### 1. Choose the profile

External customers use no Spring profile. Configure the RISE endpoint you received via the [gematik Anfrageportal](https://service.gematik.de/servicedesk/customer/portal/37) in `application.yaml`.

#### 2. Start the PoPP-Client locally

External customers:

```bash
./mvnw -pl popp-client spring-boot:run
```

#### 3. Start the PoPP-Client via Docker

```bash
docker run --rm -p 8081:8081 -p 9001:9001 \
  -v "$PWD/docker/zeta/smcb-private/smcb_private.p12:/app/smcb_private.p12:ro" \
  -e ZETA_AUTHENTICATION_SMB_KEYFILE=/app/smcb_private.p12 \
  local/popp/popp-client:latest
```

#### 4. Verify startup as above

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

The client provides the following POST endpoint to generate a PoPP-Token:

```
POST http://localhost:8081/token
```

With Request Body:

```json
{
  "communicationType": "<one of the supported types>",
  "clientSessionId": "<optional>"
}
```

The request parameter `clientsessionid` is optional. If set, the `clientsessionid` will overwrite the Konnektor `clientsessionid` from `StartCardSession`

The communication type must be one of the following:

- `contact-standard`
  - use contact-based interface from Standard-Kartenleser
- `contactless-standard`
  - use contactless interface from Standard-Kartenleser
- `contact-virtual`
  - use a virtual card from a card image file, no card reader needed
- `contact-connector`
  - use contact-based interface from eHealth-Kartenterminal via Konnektor
- `contactless-connector`
  - use contactless interface from eHealth-Kartenterminal via Konnektor
- `contact-connector-via-standard-terminal`
  - generate sample messages for Konnektor via contact-based interface from Standard-Kartenleser \

Please ensure before using a contactless card reader or connector that the hash values of the eGK have already been stored in the hash DB.

### Example usage

To generate a PoPP token, you can use the Swagger UI. Open the following URL in your browser:
[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

Alternatively, you can use this `curl` command in the terminal, e.g.:

```bash
curl -X POST http://localhost:8081/token \
  -H "Content-Type: application/json" \
  -d '{
    "communicationType": "contact-standard",
    "clientSessionId": "123456"
  }'
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

Copyright 2025-2026 gematik GmbH

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
