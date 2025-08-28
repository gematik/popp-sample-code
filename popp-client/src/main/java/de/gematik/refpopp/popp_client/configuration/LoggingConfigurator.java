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

package de.gematik.refpopp.popp_client.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Configures logging for the SOAP web service client.
 *
 * <p>This component sets the logging level for sent and received messages to TRACE if the
 * 'connector.log-ws' property is set to true.
 */
@Component
public class LoggingConfigurator {

  private final boolean logWs;

  public LoggingConfigurator(@Value("${connector.log-ws:false}") boolean logWs) {
    this.logWs = logWs;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void configureLogging() {
    if (!logWs) {
      return;
    }
    LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    loggingSystem.setLogLevel("org.springframework.ws.client.MessageTracing.sent", LogLevel.TRACE);
    loggingSystem.setLogLevel(
        "org.springframework.ws.client.MessageTracing.received", LogLevel.TRACE);
  }
}
