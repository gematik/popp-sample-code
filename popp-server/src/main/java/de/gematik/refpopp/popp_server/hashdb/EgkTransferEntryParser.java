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
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1SequenceParser;
import org.bouncycastle.asn1.ASN1SetParser;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EgkTransferEntryParser {

  private final CMSSignedDataParserFactory cmsSignedDataParserFactory;
  private final ASN1StreamParserFactory asn1StreamParserFactory;

  public EgkTransferEntryParser(
      final CMSSignedDataParserFactory cmsSignedDataParserFactory,
      final ASN1StreamParserFactory asn1StreamParserFactory) {
    this.cmsSignedDataParserFactory = cmsSignedDataParserFactory;
    this.asn1StreamParserFactory = asn1StreamParserFactory;
  }

  public List<EgkTransferEntry> parseAll(final InputStream rawCmsContent, final String sessionId) {
    final List<EgkTransferEntry> result = new ArrayList<>();

    try {
      final var cmsParser = cmsSignedDataParserFactory.createParser(rawCmsContent, sessionId);
      final var contentStream = cmsParser.getSignedContent().getContentStream();
      final var contentParser = asn1StreamParserFactory.create(contentStream);
      final var seqParser = (ASN1SequenceParser) contentParser.readObject();
      seqParser.readObject();
      final var infos = (ASN1SequenceParser) seqParser.readObject();

      ASN1Encodable infoObj;
      while ((infoObj = infos.readObject()) != null) {
        final var setParser = (ASN1SetParser) infoObj;
        final var transferEntry = parseSingleEgkInfo(setParser, sessionId);
        result.add(transferEntry);
      }
    } catch (final IOException | ParseException e) {
      throw new ImportDataException(
          sessionId, "Error streaming CMS eContent parse: " + e.getMessage(), "errorCode");
    }
    return result;
  }

  private EgkTransferEntry parseSingleEgkInfo(
      final ASN1SetParser asn1SetParser, final String sessionId)
      throws IOException, ParseException {
    ASN1UTCTime notAfter = null;
    ASN1OctetString cvcOctets = null;
    ASN1BitString autBits = null;
    ASN1Encodable field;
    while ((field = asn1SetParser.readObject()) != null) {
      final var prim = field.toASN1Primitive();
      switch (prim) {
        case final ASN1UTCTime ignored -> notAfter = ASN1UTCTime.getInstance(prim);
        case final ASN1OctetString ignored -> cvcOctets = ASN1OctetString.getInstance(prim);
        case final ASN1BitString ignored -> autBits = ASN1BitString.getInstance(prim);
        default -> log.debug("Ignoring unexpected element in egkInfo-SET: {}", prim.getClass());
      }
    }
    if (notAfter == null || cvcOctets == null || autBits == null) {
      throw new ImportDataException(sessionId, "| Missing fields in egkInfo", "errorCode");
    }
    final Date notAfterDate = notAfter.getDate();
    return EgkTransferEntry.builder()
        .cvcHash(cvcOctets.getOctets())
        .autHash(autBits.getBytes())
        .notAfter(LocalDateTime.ofInstant(notAfterDate.toInstant(), ZoneId.of("Europe/Berlin")))
        .build();
  }
}
