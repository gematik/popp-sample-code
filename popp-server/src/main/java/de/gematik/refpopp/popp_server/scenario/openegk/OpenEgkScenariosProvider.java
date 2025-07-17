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

package de.gematik.refpopp.popp_server.scenario.openegk;

import de.gematik.refpopp.popp_server.scenario.common.provider.AbstractCardScenarios;
import de.gematik.refpopp.popp_server.scenario.common.provider.CommunicationMode;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

@ConfigurationProperties(prefix = "open-egk-scenarios")
@PropertySource("classpath:open-egk-scenarios.yaml")
@Primary
public class OpenEgkScenariosProvider extends AbstractCardScenarios {

  public OpenEgkScenariosProvider(final List<Scenario> scenarios) {
    super(scenarios);
  }

  @Override
  public CommunicationMode getSupportedCommunicationMode() {
    return CommunicationMode.UNDEFINED;
  }
}
