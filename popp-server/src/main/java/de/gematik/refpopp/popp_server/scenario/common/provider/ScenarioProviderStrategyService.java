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

@Service
public class ScenarioProviderStrategyService {

  private final Map<CommunicationMode, CardScenarioProvider> strategyMap;

  public ScenarioProviderStrategyService(final List<CardScenarioProvider> providers) {
    this.strategyMap =
        providers.stream()
            .collect(
                Collectors.toMap(
                    CardScenarioProvider::getSupportedCommunicationMode, provider -> provider));
  }

  public CardScenarioProvider getProvider(final CommunicationMode version) {
    final CardScenarioProvider provider = strategyMap.get(version);
    if (provider == null) {
      throw new IllegalArgumentException("No CardScenarioProvider found for version: " + version);
    }
    return provider;
  }
}
