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

package de.gematik.refpopp.popp_server.scenario.common.provider;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Selects the scenario processing service for a communication mode.
 *
 * <p>Available processing services are indexed by the communication mode they support when this
 * service is created.
 */
@Service
public class ScenarioProcessingProviderStrategyService {

  private final Map<CommunicationMode, AbstractScenarioProcessingService> strategyMap;

  /**
   * Creates a strategy service from the available scenario processing services.
   *
   * @param processingServices the processing services to index by communication mode
   */
  public ScenarioProcessingProviderStrategyService(
      final List<AbstractScenarioProcessingService> processingServices) {
    this.strategyMap =
        processingServices.stream()
            .collect(
                Collectors.toMap(
                    AbstractScenarioProcessingService::getSupportedCommunicationMode,
                    provider -> provider));
  }

  /**
   * Returns the processing service for the specified communication mode.
   *
   * @param version the communication mode for which a provider is requested
   * @return the matching scenario processing service
   * @throws IllegalArgumentException if no service supports the specified communication mode
   */
  public AbstractScenarioProcessingService getProvider(final CommunicationMode version) {
    final AbstractScenarioProcessingService provider = strategyMap.get(version);
    if (provider == null) {
      throw new IllegalArgumentException("No ProcessingService found for version: " + version);
    }
    return provider;
  }
}
