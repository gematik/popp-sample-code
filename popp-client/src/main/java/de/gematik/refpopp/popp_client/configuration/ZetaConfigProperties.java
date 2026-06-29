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

import io.ktor.client.plugins.logging.LogLevel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zeta")
@Data
public class ZetaConfigProperties {

  private Client client;
  private Storage storage;
  private Authentication authentication;
  private LogLevel httpLogLevel;

  @Data
  public static class Client {
    private boolean disableServerValidation;
  }

  @Data
  public static class Storage {
    private String aesB64Key;
  }

  @Data
  public static class Authentication {
    private Smb smb;
  }

  @Data
  public static class Smb {
    private String alias;
    private String keyfile;
    private String password;
  }
}
