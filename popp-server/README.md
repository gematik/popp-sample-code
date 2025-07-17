# PoPP Server

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