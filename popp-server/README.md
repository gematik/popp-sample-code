_# PoPP Server

The PoPP server is a simple WebSocket server that handles communication with the client. 
When the connection is established, the server waits for the first message ([StartMessage](../popp-commons/src/main/java/de/gematik/poppcommons/api/messages/StartMessage.java)) from the client. After
receiving the message, the server processes it and sends a response as a [StandardScenarioMessage](../popp-commons/src/main/java/de/gematik/poppcommons/api/messages/StandardScenarioMessage.java)
object containing the first scenario to the client. The client answers with a [ScenarioResponseMessage](../popp-commons/src/main/java/de/gematik/poppcommons/api/messages/ScenarioResponseMessage.java). 
Once all scenarios have been processed, the server generates a
JWT token and sends it to the client as a [TokenMessage](../popp-commons/src/main/java/de/gematik/poppcommons/api/messages/TokenMessage.java).
## Running the application
```bash 
 ../mvnw clean install
 ../mvnw spring-boot:run 
```

## How does it work?

The main entry point for the server is the
[WebSocketHandler](src/main/java/de/gematik/refpopp/popp_server/handler/WebSocketHandler.java)
class.
This class is responsible for managing secure WebSocket (TLS) connections and processing messages.
The WebSocketHandler class overrides the handleTextMessage method to to process incoming client
messages. The configuration class for the WebSocketHandler is defined in
the [WebSocketConfig](src/main/java/de/gematik/refpopp/popp_server/configuration/WebSocketConfig.java)
class.

### Scenarios

The eHC scenarios are defined in the [scenarios.yaml](src/main/resources/contact-based-scenarios.yaml) file. 
The [AbstractCardScenarios](src/main/java/de/gematik/refpopp/popp_server/scenario/provider/AbstractCardScenarios.java)
class is responsible for reading and managing these scenarios.


### JWT Token Configuration

Values can be overridden per environment:

**Development/Production (application-dev.yaml/application.yaml):**

```yaml
jwt-token:
  popp:
    actor-id: "telematik-id"                     # Actor's Telematik ID
    actor-profession-oid: "1.2.276.0.76.4.50"   # OID for professional affiliation
    authorization-details: "details"              # Authorization details
```

### Default Values

If no configuration is provided, the following default values are used:
- `actor-id`: "telematik-id"
- `actor-profession-oid`: "1.2.276.0.76.4.50"
- `authorization-details`: "details"

### Import Reports API

The server provides REST endpoints to access import operation reports:

#### Endpoints

- `GET /import-reports` - Returns all import reports ordered by start time (newest first)
- `GET /import-reports/{sessionId}` - Returns a specific import report by session ID
- `GET /import-reports/latest` - Returns the most recent import report

These endpoints allow monitoring and troubleshooting of import operations. The response contains detailed information about each import session, including start time, completion status, and operation metrics.

Example curl commands:
```bash
# Get all import reports
curl -X GET http://localhost:5432/import-reports

# Get a specific import report by session ID
curl -X GET http://localhost:5432/import-reports/session123

# Get the latest import report
curl -X GET http://localhost:5432/import-reports/latest
```

Example response:
```json
{
  "sessionId": "session123",
  "startTime": "2023-08-01T14:30:06.258",
  "endTime": "2023-08-01T14:35:12.429",
  "status": "COMPLETED",
  "totalProcessed": 150,
  "successCount": 148,
  "errorCount": 2
}
```
