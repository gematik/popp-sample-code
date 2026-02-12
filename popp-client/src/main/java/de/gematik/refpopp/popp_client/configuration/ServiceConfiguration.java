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

import de.gematik.refpopp.popp_client.cardreader.CardReader;
import de.gematik.refpopp.popp_client.cardreader.CardReaderService;
import de.gematik.refpopp.popp_client.cardreader.Monitoring;
import de.gematik.refpopp.popp_client.cardreader.ReaderAndCardMonitoring;
import de.gematik.refpopp.popp_client.cardreader.card.CardEventPublisher;
import java.util.concurrent.Executors;
import javax.smartcardio.TerminalFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ServiceConfiguration {

  private final CardEventPublisher cardEventPublisher;

  public ServiceConfiguration(final CardEventPublisher cardEventPublisher) {
    this.cardEventPublisher = cardEventPublisher;
  }

  @Bean
  public CardReader cardReader(@Value("${card-reader.name}") final String cardReaderName) {
    return new CardReaderService(
        readerAndCardMonitoring(),
        TerminalFactory.getDefault(),
        Executors.newSingleThreadScheduledExecutor(),
        cardReaderName);
  }

  @Bean
  public Monitoring readerAndCardMonitoring() {
    return new ReaderAndCardMonitoring(TerminalFactory.getDefault(), cardEventPublisher);
  }

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
