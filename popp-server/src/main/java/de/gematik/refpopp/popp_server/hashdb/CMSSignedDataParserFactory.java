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

package de.gematik.refpopp.popp_server.hashdb;

import de.gematik.poppcommons.api.exceptions.ImportDataException;
import java.io.InputStream;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

@Component
public class CMSSignedDataParserFactory {
  private static final String PROVIDER = "BC";
  private final JcaDigestCalculatorProviderBuilder digestProviderBuilder;

  public CMSSignedDataParserFactory() {
    this.digestProviderBuilder = new JcaDigestCalculatorProviderBuilder().setProvider(PROVIDER);
  }

  public CMSSignedDataParser createParser(final InputStream dataStream, final String sessionId) {
    try {
      return new CMSSignedDataParser(digestProviderBuilder.build(), dataStream);
    } catch (final CMSException | OperatorCreationException e) {
      throw new ImportDataException(
          sessionId, "Failed to create CMSSignedDataParser " + e, "errorCode");
    }
  }
}
