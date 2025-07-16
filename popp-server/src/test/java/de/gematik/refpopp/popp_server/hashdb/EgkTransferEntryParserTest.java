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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ImportDataException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1SequenceParser;
import org.bouncycastle.asn1.ASN1SetParser;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EgkTransferEntryParserTest {

  @Mock private CMSSignedDataParserFactory cmsSignedDataParserFactory;

  @Mock private ASN1StreamParserFactory asn1StreamParserFactory;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CMSSignedDataParser cmsSignedDataParser;

  @InjectMocks private EgkTransferEntryParser sut;

  private static final String SESSION_ID = "test-session";

  @BeforeEach
  void setUp() {
    when(cmsSignedDataParserFactory.createParser(any(InputStream.class), eq(SESSION_ID)))
        .thenReturn(cmsSignedDataParser);
  }

  @Test
  void parseAllParsesSingleEgkEntrySuccessfully() throws Exception {
    // given
    final var contentParser = mock(org.bouncycastle.asn1.ASN1StreamParser.class);
    when(asn1StreamParserFactory.create(any(InputStream.class))).thenReturn(contentParser);

    final var seqParser = mock(ASN1SequenceParser.class);
    final var dummyEnc = mock(ASN1Encodable.class);
    final var infosParser = mock(ASN1SequenceParser.class);

    when(contentParser.readObject()).thenReturn(seqParser);
    when(seqParser.readObject())
        .thenReturn(dummyEnc) // discard
        .thenReturn(infosParser);

    final var setParser = mock(ASN1SetParser.class);
    when(infosParser.readObject()).thenReturn(setParser).thenReturn(null);

    final var utcTime = new ASN1UTCTime("230101120000Z"); // 2023-01-01T12:00:00Z
    final byte[] expectedCvc = new byte[] {0x0A, 0x0B, 0x0C};
    final var octets = new DEROctetString(expectedCvc);
    final byte[] expectedAut = new byte[] {0x0D, 0x0E, 0x0F};
    final var bits = new DERBitString(expectedAut);

    when(setParser.readObject())
        .thenReturn(utcTime)
        .thenReturn(octets)
        .thenReturn(bits)
        .thenReturn(null);

    // when
    final List<EgkTransferEntry> result = sut.parseAll(mock(InputStream.class), SESSION_ID);

    // then
    assertThat(result)
        .hasSize(1)
        .allSatisfy(
            entry -> {
              assertThat(entry.getCvcHash()).isEqualTo(expectedCvc);
              assertThat(entry.getAutHash()).isEqualTo(expectedAut);
              final var expectedLocal =
                  LocalDateTime.ofInstant(
                      utcTime.getDate().toInstant(), ZoneId.of("Europe/Berlin"));
              assertThat(entry.getNotAfter()).isEqualTo(expectedLocal);
            });

    verify(asn1StreamParserFactory).create(any(InputStream.class));
    verify(contentParser).readObject();
    verify(seqParser, times(2)).readObject();
    verify(infosParser, times(2)).readObject();
    verify(setParser, atLeastOnce()).readObject();
  }

  @Test
  void parseAllThrowsWhenFieldsMissingInSet() throws Exception {
    // given
    final var contentParser = mock(org.bouncycastle.asn1.ASN1StreamParser.class);
    when(asn1StreamParserFactory.create(any(InputStream.class))).thenReturn(contentParser);

    final var seqParser = mock(ASN1SequenceParser.class);
    final var dummyEnc = mock(ASN1Encodable.class);
    final var infosParser = mock(ASN1SequenceParser.class);

    when(contentParser.readObject()).thenReturn(seqParser);
    when(seqParser.readObject()).thenReturn(dummyEnc).thenReturn(infosParser);

    final var setParser = mock(ASN1SetParser.class);
    final var utcTime = new ASN1UTCTime("230101120000Z");
    final var octets = new DEROctetString(new byte[] {0x01});
    when(infosParser.readObject()).thenReturn(setParser).thenReturn(null);
    when(setParser.readObject()).thenReturn(utcTime).thenReturn(octets).thenReturn(null);

    // when / then
    assertThatThrownBy(() -> sut.parseAll(mock(InputStream.class), SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("| Missing fields in egkInfo");
  }
}
