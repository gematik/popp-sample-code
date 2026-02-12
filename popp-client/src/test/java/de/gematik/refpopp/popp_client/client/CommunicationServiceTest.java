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

package de.gematik.refpopp.popp_client.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.openhealth.healthcard.*;
import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.poppcommons.api.messages.StartMessage;
import de.gematik.refpopp.popp_client.cardreader.card.CardCommunicationService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.events.PaceInitializationCompleteEvent;
import de.gematik.refpopp.popp_client.client.events.TextMessageReceivedEvent;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class CommunicationServiceTest {

  private CommunicationService sut;
  private ClientServerCommunicationService clientServerCommunicationServiceMock;
  private CardCommunicationService cardCommunicationServiceMock;
  private ConnectorCommunicationServiceWrapper connectorCommunicationServiceWrapper;
  private ObjectMapper mapper;
  private Map<String, CompletableFuture<String>> tokenQueue;
  private VirtualCardService virtualCardServiceMock;

  @BeforeEach
  void setUp() throws NoSuchFieldException, IllegalAccessException {
    clientServerCommunicationServiceMock = mock(ClientServerCommunicationService.class);
    mapper = new ObjectMapper();
    cardCommunicationServiceMock = mock(CardCommunicationService.class);
    connectorCommunicationServiceWrapper = mock(ConnectorCommunicationServiceWrapper.class);
    tokenQueue = new ConcurrentHashMap<>();
    virtualCardServiceMock = mock(VirtualCardService.class);
    sut =
        new CommunicationService(
            mapper,
            cardCommunicationServiceMock,
            clientServerCommunicationServiceMock,
            connectorCommunicationServiceWrapper,
            virtualCardServiceMock);
    final var tokenQueueField = CommunicationService.class.getDeclaredField("tokenQueue");
    tokenQueueField.setAccessible(true);
    tokenQueueField.set(sut, tokenQueue);
  }

  private void mockImmediateTokenResponse() {
    doAnswer(
            inv -> {
              String tokenMsg =
                  """
                  {"type":"Token","token":"dummy-token","pn":"pn"}
                  """;
              sut.handleServerEvent(new TextMessageReceivedEvent(tokenMsg));
              return null;
            })
        .when(clientServerCommunicationServiceMock)
        .sendMessage(any(PoPPMessage.class));
  }

  private SSLSession prepareMockSslSession(String sessionId, CardConnectionType type) {
    SSLSession ssl = mock(SSLSession.class);
    when(ssl.getValue("clientSessionId")).thenReturn(sessionId);
    when(ssl.getValue("cardConnectionType")).thenReturn(type);
    return ssl;
  }

  @Test
  void startConnectsToWebSocketAndSendsStartMessage() {
    final String clientSessionId = "1";
    SSLSession ssl = prepareMockSslSession(clientSessionId, CardConnectionType.CONTACT_STANDARD);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    mockImmediateTokenResponse();
    var captor = ArgumentCaptor.forClass(PoPPMessage.class);

    sut.start(CardConnectionType.CONTACT_STANDARD, clientSessionId);

    verify(clientServerCommunicationServiceMock).connect();
    verify(clientServerCommunicationServiceMock).sendMessage(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(StartMessage.class);
  }

  @Test
  void startConnectsToWebSocketAndSendsStartMessageWithContactConnectorAndClientSessionId() {
    String clientSessionId = UUID.randomUUID().toString();
    SSLSession ssl = prepareMockSslSession(clientSessionId, CardConnectionType.CONTACT_CONNECTOR);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);

    mockImmediateTokenResponse();
    var captor = ArgumentCaptor.forClass(PoPPMessage.class);

    sut.start(CardConnectionType.CONTACT_CONNECTOR, clientSessionId);

    // then
    verify(clientServerCommunicationServiceMock).connect();
    verify(clientServerCommunicationServiceMock).sendMessage(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(StartMessage.class);
    assertThat(((StartMessage) captor.getValue()).getClientSessionId()).isEqualTo(clientSessionId);
  }

  @Test
  void startConnectsToWebSocketAndSendsStartMessageWithEmptyClientSessionId() {
    final String clientSessionId = "";
    when(connectorCommunicationServiceWrapper.startCardSession(any())).thenReturn("connectorUUID");
    SSLSession ssl = prepareMockSslSession("connectorUUID", CardConnectionType.CONTACT_CONNECTOR);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    mockImmediateTokenResponse();
    final var captor = ArgumentCaptor.forClass(PoPPMessage.class);

    sut.start(CardConnectionType.CONTACT_CONNECTOR, clientSessionId);

    verify(clientServerCommunicationServiceMock).connect();
    verify(clientServerCommunicationServiceMock).sendMessage(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(StartMessage.class);
    assertThat(((StartMessage) captor.getValue()).getClientSessionId()).isEqualTo("connectorUUID");
  }

  @Test
  void startConnectsToWebSocketAndSendsStartMessageWithContactStandardAndClientSessionId() {
    final String clientSessionId = UUID.randomUUID().toString();
    SSLSession ssl = prepareMockSslSession(clientSessionId, CardConnectionType.CONTACT_STANDARD);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    mockImmediateTokenResponse();
    final var captor = ArgumentCaptor.forClass(PoPPMessage.class);

    sut.start(CardConnectionType.CONTACT_STANDARD, clientSessionId);

    verify(clientServerCommunicationServiceMock).connect();
    verify(clientServerCommunicationServiceMock).sendMessage(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(StartMessage.class);
    assertThat(((StartMessage) captor.getValue()).getClientSessionId()).isEqualTo(clientSessionId);
  }

  @Test
  void startSendsStartMessageIfAlreadyConnectedToServer() {
    final String clientSessionId = "";
    final var captor = ArgumentCaptor.forClass(PoPPMessage.class);
    SSLSession ssl = prepareMockSslSession(clientSessionId, CardConnectionType.CONTACT_STANDARD);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    mockImmediateTokenResponse();

    sut.start(CardConnectionType.CONTACT_STANDARD, clientSessionId);

    verify(clientServerCommunicationServiceMock).connect();
    verify(clientServerCommunicationServiceMock).sendMessage(captor.capture());
    final StartMessage message = (StartMessage) captor.getValue();
    assertThat(message.getClientSessionId()).isEqualTo(clientSessionId);
  }

  @Test
  void startContactStandardWithSecureChannel() {
    final String clientSessionId = UUID.randomUUID().toString();
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(mock(SSLSession.class));

    final var secureChannelMock = mock(SecureChannel.class);
    when(cardCommunicationServiceMock.getSecureChannel())
        .thenReturn(Optional.of(secureChannelMock));

    assertThatThrownBy(() -> sut.start(CardConnectionType.CONTACT_STANDARD, clientSessionId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Contact connection requested but card is contactless.");
    verify(clientServerCommunicationServiceMock, never()).connect();
  }

  @Test
  void handlePaceInitializationCompleteStartsContactlessService() {
    final var secureChannelMock = mock(SecureChannel.class);
    when(cardCommunicationServiceMock.getSecureChannel())
        .thenReturn(Optional.of(secureChannelMock));
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(mock(SSLSession.class));

    sut.start(CardConnectionType.CONTACTLESS_STANDARD, "test-session");
    sut.handlePaceInitializationComplete(new PaceInitializationCompleteEvent());

    verify(clientServerCommunicationServiceMock).connect();
  }

  @Test
  void handlePaceInitializationCompleteFailsWhenPaceFailed() {
    when(cardCommunicationServiceMock.getSecureChannel()).thenReturn(Optional.empty());

    sut.start(CardConnectionType.CONTACTLESS_STANDARD, "test-session");

    assertThatThrownBy(
            () -> sut.handlePaceInitializationComplete(new PaceInitializationCompleteEvent()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Contactless connection requested but card is contact-based.");
  }

  @Test
  void startContactlessStandardWaitsForPaceInitialization() {
    when(cardCommunicationServiceMock.getSecureChannel()).thenReturn(Optional.empty());

    sut.start(CardConnectionType.CONTACTLESS_STANDARD, "test-session");

    verify(clientServerCommunicationServiceMock, never()).connect();
  }

  @Test
  void handlePaceInitializationCompleteStartsPendingService() {
    final var secureChannelMock = mock(SecureChannel.class);
    when(cardCommunicationServiceMock.getSecureChannel())
        .thenReturn(Optional.of(secureChannelMock));
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(mock(SSLSession.class));

    // Start contactless connection which should wait for PACE
    sut.start(CardConnectionType.CONTACTLESS_STANDARD, "test-session");
    verify(clientServerCommunicationServiceMock, never()).connect();

    sut.handlePaceInitializationComplete(new PaceInitializationCompleteEvent());

    verify(clientServerCommunicationServiceMock).connect();
    verify(clientServerCommunicationServiceMock).sendMessage(any(StartMessage.class));
  }

  @Test
  void startContactStandardWithSecureChannelThrowsException() {
    final var secureChannelMock = mock(SecureChannel.class);
    when(cardCommunicationServiceMock.getSecureChannel())
        .thenReturn(Optional.of(secureChannelMock));

    assertThatThrownBy(() -> sut.start(CardConnectionType.CONTACT_STANDARD, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Contact connection requested but card is contactless.");
    verify(clientServerCommunicationServiceMock, never()).connect();
  }

  @Test
  void startVirtualCardConnectsToWebSocketAndSendsStartMessage() {
    SSLSession ssl = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    when(ssl.getValue("clientSessionId")).thenReturn("mock-session");

    doAnswer(
            inv -> {
              sut.handleServerEvent(
                  new TextMessageReceivedEvent(
                      "{\"type\":\"Token\",\"token\":\"mock-token\",\"pn\":\"pn\"}"));
              return null;
            })
        .when(clientServerCommunicationServiceMock)
        .sendMessage(any());

    String token = sut.startVirtualCard(CardConnectionType.CONTACT_STANDARD, "mock-session");

    assertThat(token).isEqualTo("mock-token");
    verify(clientServerCommunicationServiceMock).connect();
    verify(ssl).putValue("virtualCard", true);
    verify(ssl).putValue("cardConnectionType", CardConnectionType.CONTACT_STANDARD);
  }

  @Test
  void handleServerEventProcessesWithScenarioMessage() {
    final var givenMessage =
        """
            {
              "version": "1.0.0",
              "clientSessionId": "12345-abcde-67890",
              "sequenceCounter": 1,
              "timeSpan": 300,
              "steps": [
                {
                  "commandApdu": "00A404000E325041592E5359532E4444463031",
                  "expectedStatusWords": [
                    "9000",
                    "6A82"
                  ]
                },
                {
                  "commandApdu": "00B2010C00",
                  "expectedStatusWords": [
                    "9000"
                  ]
                }
              ],
              "type": "StandardScenario"
            }
        """;
    final var scenarioStep1 =
        new ScenarioStep("00A404000E325041592E5359532E4444463031", List.of("9000", "6A82"));
    final var scenarioStep2 = new ScenarioStep("00B2010C00", List.of("9000"));
    final var scenarioSteps = List.of(scenarioStep1, scenarioStep2);

    final var event = new TextMessageReceivedEvent(givenMessage);
    when(cardCommunicationServiceMock.process(anyList())).thenReturn(List.of("data"));
    final var sslSessionMock = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(sslSessionMock);

    sut.handleServerEvent(event);

    verify(cardCommunicationServiceMock).process(scenarioSteps);
    verify(clientServerCommunicationServiceMock).sendMessage(any(ScenarioResponseMessage.class));
  }

  @Test
  void handleServerEventProcessesWithTokenMessage() {
    final var givenMessage =
        """
        {"type":"Token","token":"token","pn":"pn"}
        """;
    final var event = new TextMessageReceivedEvent(givenMessage);

    SSLSession ssl = mock(SSLSession.class);
    when(ssl.getValue("clientSessionId")).thenReturn("dummy-session-id");
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);

    sut.handleServerEvent(event);

    verify(clientServerCommunicationServiceMock, times(2)).getSSLSession();
    verifyNoInteractions(cardCommunicationServiceMock);
  }

  @Test
  void handleServerEventProcessesWithTokenMessageAndConnector() {
    final var givenMessage =
        """
        {"type":"Token","token":"token","pn":"pn"}
        """;
    final var event = new TextMessageReceivedEvent(givenMessage);
    final var sslSessionMock = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(sslSessionMock);
    when(sslSessionMock.getValue("cardConnectionType"))
        .thenReturn(CardConnectionType.CONTACT_CONNECTOR);
    when(sslSessionMock.getValue("clientSessionId")).thenReturn("clientSessionId");

    sut.handleServerEvent(event);

    verify(clientServerCommunicationServiceMock, times(2)).getSSLSession();
    verify(connectorCommunicationServiceWrapper).stopCardSession("clientSessionId");
    verifyNoInteractions(cardCommunicationServiceMock);
  }

  @Test
  void handleServerEventProcessesWithConnectorScenarioMessage() {
    final var givenMessage =
        """
        {"version":"1.0.0","signedScenario":"eyJ4NWMiOlsiTUlJQzVEQ0NBb3VnQXdJQkFnSUhBWkpJWUxPZ0REQUtCZ2dxaGtqT1BRUURBakNCaERFTE1Ba0dBMVVFQmhNQ1JFVXhIekFkQmdOVkJBb01GbWRsYldGMGFXc2dSMjFpU0NCT1QxUXRWa0ZNU1VReE1qQXdCZ05WQkFzTUtVdHZiWEJ2Ym1WdWRHVnVMVU5CSUdSbGNpQlVaV3hsYldGMGFXdHBibVp5WVhOMGNuVnJkSFZ5TVNBd0hnWURWUVFEREJkSFJVMHVTMDlOVUMxRFFUWXhJRlJGVTFRdFQwNU1XVEFlRncweU5UQXhNall5TXpBd01EQmFGdzB6TURBeE1qWXlNalU1TlRsYU1Gc3hDekFKQmdOVkJBWVRBa1JGTVNZd0pBWURWUVFLREIxblpXMWhkR2xySUZSRlUxUXRUMDVNV1NBdElFNVBWQzFXUVV4SlJERWtNQ0lHQTFVRUF3d2JjRzl3Y0M1blpXMWhkR2xyTG5SbGJHVnRZWFJwYXkxMFpYTjBNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUUrU3dEV0RYTEFtVlVhQ0U3VjZkQkpKWWZRQkVUQXkwa0R4MHFEM2pqOTFyR01QNEdHYnFoUFNBQlA0Qll6MG9nWmRuaGlkRDlxbXRhTjMxVWx6TkdsYU9DQVE0d2dnRUtNQXdHQTFVZEV3RUIvd1FDTUFBd0lRWURWUjBnQkJvd0dEQUtCZ2dxZ2hRQVRBU0NIekFLQmdncWdoUUFUQVNCSXpBN0JnZ3JCZ0VGQlFjQkFRUXZNQzB3S3dZSUt3WUJCUVVITUFHR0gyaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2WldOakxXOWpjM0F3RGdZRFZSMFBBUUgvQkFRREFnWkFNQjBHQTFVZERnUVdCQlNjSWtyb3hTTmdaaHAvWnFsNmRvSXhCV2hvT0RCS0JnVXJKQWdEQXdSQk1EOHdQVEE3TURrd056QXBEQ2RRY205dlppQnZaaUJRWVhScFpXNTBJRkJ5WlhObGJtTmxJQ2hRYjFCUUtTQkVhV1Z1YzNRd0NnWUlLb0lVQUV3RWdpVXdId1lEVlIwakJCZ3dGb0FVbnpYZ01LbC95dmhtbjVBS1FzMjdnV1dmU2Y0d0NnWUlLb1pJemowRUF3SURSd0F3UkFJZ0VzWi84RUI3REQ1UGEwMU03Rkl6TFZaZUdKUU5aTklaNWxGWXpCQVpuZHNDSUgzTGRrNGwxdFUzSEJNZmhacnJtczE5ZFVNcml4UmFpN29zczV5dDNtalQiXSwidHlwIjoiSldUIiwiYWxnIjoiRVMyNTYiLCJ4NXQjUzI1NiI6ImZaeFlMQ2tLRkV2a2hIaEJhbFl5eVUzTlFLU2dwTS1JcVBDMVFWMjJhQlUifQ.eyJzdGFuZGFyZFNjZW5hcmlvTWVzc2FnZSI6eyJ0eXBlIjoiU3RhbmRhcmRTY2VuYXJpbyIsInZlcnNpb24iOiIxLjAuMCIsImNsaWVudFNlc3Npb25JZCI6ImZjNDQyM2FlLWVkMDctNDFjMy05YzBlLWExMTViZjViNmExZCIsInNlcXVlbmNlQ291bnRlciI6MCwidGltZVNwYW4iOjEwMDAwLCJzdGVwcyI6W3siYXBkdUNvbW1hbmQiOiIwMCBhNCAwNDBjICAgIDA3IEQyNzYwMDAxNDQ4MDAwIiwiZXhwZWN0ZWRTdGF0dXNXb3JkcyI6WyI5MDAwIl19LHsiYXBkdUNvbW1hbmQiOiIwMCBiMCA5MTAwICAgIDAwIiwiZXhwZWN0ZWRTdGF0dXNXb3JkcyI6WyI5MDAwIiwiNjI4MSJdfV19fQ.cbnxYTWUR4-qplK_pt9zYbzg5Dx9UPhhazPQk5d9Ghqu-FshkAqdPyERApzTTOa5ksttH_-TS-fYWARjPSoF0A", "type":"ConnectorScenario"}
        """;
    final var event = new TextMessageReceivedEvent(givenMessage);
    final var sslSessionMock = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(sslSessionMock);

    sut.handleServerEvent(event);

    // Connector interface is not implemented
    verify(clientServerCommunicationServiceMock).sendMessage(any());
    verify(connectorCommunicationServiceWrapper)
        .secureSendApdu(
            "eyJ4NWMiOlsiTUlJQzVEQ0NBb3VnQXdJQkFnSUhBWkpJWUxPZ0REQUtCZ2dxaGtqT1BRUURBakNCaERFTE1Ba0dBMVVFQmhNQ1JFVXhIekFkQmdOVkJBb01GbWRsYldGMGFXc2dSMjFpU0NCT1QxUXRWa0ZNU1VReE1qQXdCZ05WQkFzTUtVdHZiWEJ2Ym1WdWRHVnVMVU5CSUdSbGNpQlVaV3hsYldGMGFXdHBibVp5WVhOMGNuVnJkSFZ5TVNBd0hnWURWUVFEREJkSFJVMHVTMDlOVUMxRFFUWXhJRlJGVTFRdFQwNU1XVEFlRncweU5UQXhNall5TXpBd01EQmFGdzB6TURBeE1qWXlNalU1TlRsYU1Gc3hDekFKQmdOVkJBWVRBa1JGTVNZd0pBWURWUVFLREIxblpXMWhkR2xySUZSRlUxUXRUMDVNV1NBdElFNVBWQzFXUVV4SlJERWtNQ0lHQTFVRUF3d2JjRzl3Y0M1blpXMWhkR2xyTG5SbGJHVnRZWFJwYXkxMFpYTjBNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUUrU3dEV0RYTEFtVlVhQ0U3VjZkQkpKWWZRQkVUQXkwa0R4MHFEM2pqOTFyR01QNEdHYnFoUFNBQlA0Qll6MG9nWmRuaGlkRDlxbXRhTjMxVWx6TkdsYU9DQVE0d2dnRUtNQXdHQTFVZEV3RUIvd1FDTUFBd0lRWURWUjBnQkJvd0dEQUtCZ2dxZ2hRQVRBU0NIekFLQmdncWdoUUFUQVNCSXpBN0JnZ3JCZ0VGQlFjQkFRUXZNQzB3S3dZSUt3WUJCUVVITUFHR0gyaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2WldOakxXOWpjM0F3RGdZRFZSMFBBUUgvQkFRREFnWkFNQjBHQTFVZERnUVdCQlNjSWtyb3hTTmdaaHAvWnFsNmRvSXhCV2hvT0RCS0JnVXJKQWdEQXdSQk1EOHdQVEE3TURrd056QXBEQ2RRY205dlppQnZaaUJRWVhScFpXNTBJRkJ5WlhObGJtTmxJQ2hRYjFCUUtTQkVhV1Z1YzNRd0NnWUlLb0lVQUV3RWdpVXdId1lEVlIwakJCZ3dGb0FVbnpYZ01LbC95dmhtbjVBS1FzMjdnV1dmU2Y0d0NnWUlLb1pJemowRUF3SURSd0F3UkFJZ0VzWi84RUI3REQ1UGEwMU03Rkl6TFZaZUdKUU5aTklaNWxGWXpCQVpuZHNDSUgzTGRrNGwxdFUzSEJNZmhacnJtczE5ZFVNcml4UmFpN29zczV5dDNtalQiXSwidHlwIjoiSldUIiwiYWxnIjoiRVMyNTYiLCJ4NXQjUzI1NiI6ImZaeFlMQ2tLRkV2a2hIaEJhbFl5eVUzTlFLU2dwTS1JcVBDMVFWMjJhQlUifQ.eyJzdGFuZGFyZFNjZW5hcmlvTWVzc2FnZSI6eyJ0eXBlIjoiU3RhbmRhcmRTY2VuYXJpbyIsInZlcnNpb24iOiIxLjAuMCIsImNsaWVudFNlc3Npb25JZCI6ImZjNDQyM2FlLWVkMDctNDFjMy05YzBlLWExMTViZjViNmExZCIsInNlcXVlbmNlQ291bnRlciI6MCwidGltZVNwYW4iOjEwMDAwLCJzdGVwcyI6W3siYXBkdUNvbW1hbmQiOiIwMCBhNCAwNDBjICAgIDA3IEQyNzYwMDAxNDQ4MDAwIiwiZXhwZWN0ZWRTdGF0dXNXb3JkcyI6WyI5MDAwIl19LHsiYXBkdUNvbW1hbmQiOiIwMCBiMCA5MTAwICAgIDAwIiwiZXhwZWN0ZWRTdGF0dXNXb3JkcyI6WyI5MDAwIiwiNjI4MSJdfV19fQ.cbnxYTWUR4-qplK_pt9zYbzg5Dx9UPhhazPQk5d9Ghqu-FshkAqdPyERApzTTOa5ksttH_-TS-fYWARjPSoF0A");
    verify(cardCommunicationServiceMock, never()).process(anyList());
  }

  @Test
  void handleServerEventProcessesWithConnectorMockScenarioMessage() {
    final var givenMessage =
        """
        {"version":"1.0.0","signedScenario":"eyJ4NWMiOlsiTUlJQzVEQ0NBb3VnQXdJQkFnSUhBWkpJWUxPZ0REQUtCZ2dxaGtqT1BRUURBakNCaERFTE1Ba0dBMVVFQmhNQ1JFVXhIekFkQmdOVkJBb01GbWRsYldGMGFXc2dSMjFpU0NCT1QxUXRWa0ZNU1VReE1qQXdCZ05WQkFzTUtVdHZiWEJ2Ym1WdWRHVnVMVU5CSUdSbGNpQlVaV3hsYldGMGFXdHBibVp5WVhOMGNuVnJkSFZ5TVNBd0hnWURWUVFEREJkSFJVMHVTMDlOVUMxRFFUWXhJRlJGVTFRdFQwNU1XVEFlRncweU5UQXhNall5TXpBd01EQmFGdzB6TURBeE1qWXlNalU1TlRsYU1Gc3hDekFKQmdOVkJBWVRBa1JGTVNZd0pBWURWUVFLREIxblpXMWhkR2xySUZSRlUxUXRUMDVNV1NBdElFNVBWQzFXUVV4SlJERWtNQ0lHQTFVRUF3d2JjRzl3Y0M1blpXMWhkR2xyTG5SbGJHVnRZWFJwYXkxMFpYTjBNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUUrU3dEV0RYTEFtVlVhQ0U3VjZkQkpKWWZRQkVUQXkwa0R4MHFEM2pqOTFyR01QNEdHYnFoUFNBQlA0Qll6MG9nWmRuaGlkRDlxbXRhTjMxVWx6TkdsYU9DQVE0d2dnRUtNQXdHQTFVZEV3RUIvd1FDTUFBd0lRWURWUjBnQkJvd0dEQUtCZ2dxZ2hRQVRBU0NIekFLQmdncWdoUUFUQVNCSXpBN0JnZ3JCZ0VGQlFjQkFRUXZNQzB3S3dZSUt3WUJCUVVITUFHR0gyaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2WldOakxXOWpjM0F3RGdZRFZSMFBBUUgvQkFRREFnWkFNQjBHQTFVZERnUVdCQlNjSWtyb3hTTmdaaHAvWnFsNmRvSXhCV2hvT0RCS0JnVXJKQWdEQXdSQk1EOHdQVEE3TURrd056QXBEQ2RRY205dlppQnZaaUJRWVhScFpXNTBJRkJ5WlhObGJtTmxJQ2hRYjFCUUtTQkVhV1Z1YzNRd0NnWUlLb0lVQUV3RWdpVXdId1lEVlIwakJCZ3dGb0FVbnpYZ01LbC95dmhtbjVBS1FzMjdnV1dmU2Y0d0NnWUlLb1pJemowRUF3SURSd0F3UkFJZ0VzWi84RUI3REQ1UGEwMU03Rkl6TFZaZUdKUU5aTklaNWxGWXpCQVpuZHNDSUgzTGRrNGwxdFUzSEJNZmhacnJtczE5ZFVNcml4UmFpN29zczV5dDNtalQiXSwidHlwIjoiSldUIiwic3RwbCI6Ik1JSUVZd29CQUtDQ0JGd3dnZ1JZQmdrckJnRUZCUWN3QVFFRWdnUkpNSUlFUlRDQithSVdCQlR0enlFNU5yN0JNN2Mxbkx5bkdNYjZNcWlyM0JnUE1qQXlOVEF6TURReE5ETTNORGxhTUlHb01JR2xNRHN3Q1FZRkt3NERBaG9GQUFRVXYyUi82MUt6VGVWekJ6dk0xbkVJTXQwSkhTSUVGR0l0allaUWxaYklkYVJSNmtpWTBSVEpYeTBkQWdJQmtZQUFHQTh5TURJMU1ETXdOREUwTXpjME9WcWdFUmdQTWpBeU5UQXpNRGt4TkRNM05EbGFvVUF3UGpBOEJnVXJKQWdERFFRek1ERXdEUVlKWUlaSUFXVURCQUlCQlFBRUlOVXQvMXBsc1RWb2cvdVdhOWttNFhPYVZVM0oxVTlXdUw0dFl0cXBJNThWb1NNd0lUQWZCZ2tyQmdFRkJRY3dBUUlFRWdRUWcwbDVLZXZnYWl2Ri8zZ1ZRK1RzMkRBS0JnZ3Foa2pPUFFRREFnTkhBREJFQWlBSEVQNXoxMGZZUGxSN2c3ZmJTVHdSbzIrYjlpZC9WeEovV3BEZHBqeGh1Z0lnUmdWYndpTHZUbk13Zk5LQ0tLYXh4VDg4YlAzZGpkcmY3bUNUZFJuWjVrMmdnZ0x3TUlJQzdEQ0NBdWd3Z2dLUG9BTUNBUUlDRUJNUVBOZktGb1AxM2t0U09vRVBNbVl3Q2dZSUtvWkl6ajBFQXdJd2dhc3hDekFKQmdOVkJBWVRBa1JGTVM4d0xRWURWUVFLRENaVUxWTjVjM1JsYlhNZ1NXNTBaWEp1WVhScGIyNWhiQ0JIYldKSUlFNVBWQzFXUVV4SlJERklNRVlHQTFVRUN3dy9TVzV6ZEdsMGRYUnBiMjRnWkdWeklFZGxjM1Z1WkdobGFYUnpkMlZ6Wlc1ekxVTkJJR1JsY2lCVVpXeGxiV0YwYVd0cGJtWnlZWE4wY25WcmRIVnlNU0V3SHdZRFZRUUREQmhVVTFsVFNTNVRUVU5DTFVOQk5TQlVSVk5VTFU5T1RGa3dIaGNOTWpRd01USTVNVEV6TlRBd1doY05Namt3TVRJNU1qTTFPVFU1V2pCc01Rc3dDUVlEVlFRR0V3SkVSVEV4TUM4R0ExVUVDZ3dvUkdWMWRITmphR1VnVkdWc1pXdHZiU0JUWldOMWNtbDBlU0JIYldKSUlFNVBWQzFXUVV4SlJERXFNQ2dHQTFVRUF3d2hWRk5aVTBrdVUwMURRaTFQUTFOUUxWTnBaMjVsY2pVZ1ZFVlRWQzFQVGt4Wk1Gb3dGQVlIS29aSXpqMENBUVlKS3lRREF3SUlBUUVIQTBJQUJBbTFzRUJ4YnArQTh6VndHaHYrYUV2ODBiTHIwTjF5ZTN0Ly9MRDVBSERBaG5Ba3ZBbEhsZmdTaCt1UGJwdnI1MHFtSEhkWGVQSHZKZFlxZE11RStzQ2pnZEV3Z2M0d0RnWURWUjBQQVFIL0JBUURBZ1pBTUIwR0ExVWREZ1FXQkJUdHp5RTVOcjdCTTdjMW5MeW5HTWI2TXFpcjNEQVRCZ05WSFNVRUREQUtCZ2dyQmdFRkJRY0RDVEFmQmdOVkhTTUVHREFXZ0JSaUxZMkdVSldXeUhXa1VlcEltTkVVeVY4dEhUQVZCZ05WSFNBRURqQU1NQW9HQ0NxQ0ZBQk1CSUVqTUF3R0ExVWRFd0VCL3dRQ01BQXdRZ1lJS3dZQkJRVUhBUUVFTmpBME1ESUdDQ3NHQVFVRkJ6QUJoaVpvZEhSd09pOHZiMk56Y0M1emJXTmlMblJsYzNRdWRHVnNaWE5sWXk1a1pTOXZZM053Y2pBS0JnZ3Foa2pPUFFRREFnTkhBREJFQWlBT294SlpwUlRmRUhYV2k1ZjJKakRkTnZhNktENmhCOG9GaHJQWEF0aVJzd0lnQ2NvR01HeTR4ajQxbkNBT3VTaGZCc3pNZjZrMnBKNFJ2NndoUmRsZDMyMD0iLCJhbGciOiJFUzI1NiIsIng1dCNTMjU2IjoiZlp4WUxDa0tGRXZraEhoQmFsWXl5VTNOUUtTZ3BNLUlxUEMxUVYyMmFCVSJ9.eyJtZXNzYWdlIjp7InR5cGUiOiJTdGFuZGFyZFNjZW5hcmlvIiwidmVyc2lvbiI6IjEuMC4wIiwiY2xpZW50U2Vzc2lvbklkIjoiMzg0NzQwYmEtNmFkNC00ODlkLWE0NWQtMjE3ODIxMjViOTgwIiwic2VxdWVuY2VDb3VudGVyIjowLCJ0aW1lU3BhbiI6MTAwMDAsInN0ZXBzIjpbeyJhcGR1Q29tbWFuZCI6IjAwYTQwNDBjMDdEMjc2MDAwMTQ0ODAwMCIsImV4cGVjdGVkU3RhdHVzV29yZHMiOlsiOTAwMCJdfSx7ImFwZHVDb21tYW5kIjoiMDBiMDkxMDAwMCIsImV4cGVjdGVkU3RhdHVzV29yZHMiOlsiOTAwMCIsIjYyODEiXX1dfX0.8t2qAmP-_7g3m08VtrnCW-dusApHdEmA4neO2-qjEOuKvreLrjCJ5ZwUzTYy5KiBTidqgBImK0rJsvOeh1DxxA", "type":"ConnectorScenario"}
        """;
    final var event = new TextMessageReceivedEvent(givenMessage);
    final var sslSessionMock = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(sslSessionMock);
    when(sslSessionMock.getValue("connectorMock")).thenReturn(true);

    sut.handleServerEvent(event);

    verify(clientServerCommunicationServiceMock).sendMessage(any());
    verify(cardCommunicationServiceMock).process(anyList());
  }

  @Test
  void handleServerEventProcessesWithWrongToken() {
    final var givenMessage =
        """
        {"version":"1.0.0","signedScenario":"wrong-token", "type":"ConnectorScenario"}
        """;
    final var event = new TextMessageReceivedEvent(givenMessage);
    final var sslSessionMock = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(sslSessionMock);
    when(sslSessionMock.getValue(anyString())).thenReturn(true);

    assertThatThrownBy(() -> sut.handleServerEvent(event))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid token format");

    verify(clientServerCommunicationServiceMock).getSSLSession();
    verify(clientServerCommunicationServiceMock, never()).sendMessage(any());
  }

  @Test
  void handleServerEventProcessesWithErrorMessage() {
    final var givenMessage =
        """
        {"type":"Error","errorCode":"errorCode","errorDetail":"errorDetail"}
        """;
    final var event = new TextMessageReceivedEvent(givenMessage);

    sut.handleServerEvent(event);

    verifyNoInteractions(clientServerCommunicationServiceMock);
    verifyNoInteractions(cardCommunicationServiceMock);
  }

  @Test
  void handleServerEventProcessesWithUnknownMessage() {
    final var givenMessage =
        """
        {"type":"UNKNOWN_MESSAGE","payload":"payload"}
        """;
    final var event = new TextMessageReceivedEvent(givenMessage);

    assertThrows(IllegalArgumentException.class, () -> sut.handleServerEvent(event));

    verifyNoInteractions(clientServerCommunicationServiceMock);
    verifyNoInteractions(cardCommunicationServiceMock);
  }

  @Test
  void handleServerEventHandlesJsonProcessingException() {
    final var message = "invalid json";

    assertThatThrownBy(() -> sut.handleServerEvent(new TextMessageReceivedEvent(message)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Error parsing message");

    verifyNoInteractions(clientServerCommunicationServiceMock);
  }

  @Test
  void whenStartConnectorMock_thenConnectsAndReturnsToken() {
    SSLSession ssl = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    when(ssl.getValue("clientSessionId")).thenReturn("mock-session");

    doAnswer(
            inv -> {
              sut.handleServerEvent(
                  new TextMessageReceivedEvent(
                      "{\"type\":\"Token\",\"token\":\"mock-token\",\"pn\":\"pn\"}"));
              return null;
            })
        .when(clientServerCommunicationServiceMock)
        .sendMessage(any());

    String token = sut.startConnectorMock("mock-session");

    assertThat(token).isEqualTo("mock-token");
    verify(clientServerCommunicationServiceMock).connect();
    verify(ssl).putValue(ConnectorCommunicationServiceWrapper.CONNECTOR_MOCK, true);
  }

  @Test
  void whenContactConnectorAndInvalidClientSessionId_thenConnectorSessionIsUsed() {
    when(connectorCommunicationServiceWrapper.getConnectedEgkCard()).thenReturn("egk");
    when(connectorCommunicationServiceWrapper.startCardSession("egk"))
        .thenReturn("connector-session");

    SSLSession ssl =
        prepareMockSslSession("connector-session", CardConnectionType.CONTACT_CONNECTOR);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);

    mockImmediateTokenResponse();

    String token = sut.start(CardConnectionType.CONTACT_CONNECTOR, "not-a-uuid");

    assertThat(token).isEqualTo("dummy-token");
  }

  @Test
  void whenTokenIsNotReceivedWithinTimeout_thenRuntimeExceptionIsThrown() {
    SSLSession ssl = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    when(cardCommunicationServiceMock.getSecureChannel()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.start(CardConnectionType.CONTACT_STANDARD, "session"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Token retrieval timed out");
  }

  @Test
  void whenTokenReceivedWithoutWaitingFuture_thenNoExceptionIsThrown() {
    SSLSession ssl = mock(SSLSession.class);
    when(ssl.getValue("clientSessionId")).thenReturn("missing-session");
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);

    sut.handleServerEvent(
        new TextMessageReceivedEvent("{\"type\":\"Token\",\"token\":\"t\",\"pn\":\"pn\"}"));

    verify(clientServerCommunicationServiceMock, times(2)).getSSLSession();
  }

  @Test
  void whenStopConnectorSessionThrowsUnknownSession_thenExceptionIsIgnored() {
    SSLSession ssl = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    when(ssl.getValue("cardConnectionType")).thenReturn(CardConnectionType.CONTACT_CONNECTOR);
    when(ssl.getValue("clientSessionId")).thenReturn("session-id");

    var fault = mock(org.springframework.ws.soap.client.SoapFaultClientException.class);
    when(fault.getFaultStringOrReason()).thenReturn("Unbekannte Session ID");
    doThrow(fault).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");

    sut.handleServerEvent(
        new TextMessageReceivedEvent("{\"type\":\"Token\",\"token\":\"t\",\"pn\":\"pn\"}"));

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void whenStopConnectorSessionThrowsOtherSoapFault_thenExceptionIsRethrown() {
    SSLSession ssl = mock(SSLSession.class);
    when(clientServerCommunicationServiceMock.getSSLSession()).thenReturn(ssl);
    when(ssl.getValue("cardConnectionType")).thenReturn(CardConnectionType.CONTACT_CONNECTOR);
    when(ssl.getValue("clientSessionId")).thenReturn("session-id");

    var fault = mock(org.springframework.ws.soap.client.SoapFaultClientException.class);
    when(fault.getFaultStringOrReason()).thenReturn("Other error");
    doThrow(fault).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");

    assertThatThrownBy(
            () ->
                sut.handleServerEvent(
                    new TextMessageReceivedEvent(
                        "{\"type\":\"Token\",\"token\":\"t\",\"pn\":\"pn\"}")))
        .isSameAs(fault);
  }
}
