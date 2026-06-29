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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.exceptions.ImportDataException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1SequenceParser;
import org.bouncycastle.asn1.ASN1SetParser;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EgkTransferEntryParserTest {

  @Mock private CMSSignedDataParserFactory cmsSignedDataParserFactory;

  @Mock private ASN1StreamParserFactory asn1StreamParserFactory;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CMSSignedDataParser cmsSignedDataParser;

  @Mock private CMSTypedStream cmsTypedStream;

  @Mock private InputStream cmsContentStream;

  @InjectMocks private EgkTransferEntryParser sut;

  private static final String SESSION_ID = "test-session";

  @BeforeEach
  void setUp() {
    lenient()
        .when(cmsSignedDataParserFactory.createParser(any(InputStream.class), eq(SESSION_ID)))
        .thenReturn(cmsSignedDataParser);
    lenient().when(cmsSignedDataParser.getSignedContent()).thenReturn(cmsTypedStream);
    lenient().when(cmsTypedStream.getContentStream()).thenReturn(cmsContentStream);
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

  @Test
  void parseAllParsesMultipleEgkEntriesSuccessfully() throws Exception {
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

    final var setParser1 = mock(ASN1SetParser.class);
    final var setParser2 = mock(ASN1SetParser.class);
    when(infosParser.readObject()).thenReturn(setParser1).thenReturn(setParser2).thenReturn(null);

    // First entry
    final var utcTime1 = new ASN1UTCTime("230101120000Z");
    final byte[] cvc1 = new byte[] {0x01, 0x02};
    final var octets1 = new DEROctetString(cvc1);
    final byte[] aut1 = new byte[] {0x03, 0x04};
    final var bits1 = new DERBitString(aut1);

    when(setParser1.readObject())
        .thenReturn(utcTime1)
        .thenReturn(octets1)
        .thenReturn(bits1)
        .thenReturn(null);

    // Second entry
    final var utcTime2 = new ASN1UTCTime("231201120000Z");
    final byte[] cvc2 = new byte[] {0x05, 0x06};
    final var octets2 = new DEROctetString(cvc2);
    final byte[] aut2 = new byte[] {0x07, 0x08};
    final var bits2 = new DERBitString(aut2);

    when(setParser2.readObject())
        .thenReturn(utcTime2)
        .thenReturn(octets2)
        .thenReturn(bits2)
        .thenReturn(null);

    // when
    final List<EgkTransferEntry> result = sut.parseAll(mock(InputStream.class), SESSION_ID);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getCvcHash()).isEqualTo(cvc1);
    assertThat(result.get(0).getAutHash()).isEqualTo(aut1);
    assertThat(result.get(1).getCvcHash()).isEqualTo(cvc2);
    assertThat(result.get(1).getAutHash()).isEqualTo(aut2);
  }

  @Test
  void parseAllThrowsWhenMissingNotAfter() throws Exception {
    // given
    final var contentParser = mock(org.bouncycastle.asn1.ASN1StreamParser.class);
    when(asn1StreamParserFactory.create(any(InputStream.class))).thenReturn(contentParser);

    final var seqParser = mock(ASN1SequenceParser.class);
    final var dummyEnc = mock(ASN1Encodable.class);
    final var infosParser = mock(ASN1SequenceParser.class);

    when(contentParser.readObject()).thenReturn(seqParser);
    when(seqParser.readObject()).thenReturn(dummyEnc).thenReturn(infosParser);

    final var setParser = mock(ASN1SetParser.class);
    final byte[] cvc = new byte[] {0x01};
    final var octets = new DEROctetString(cvc);
    final byte[] aut = new byte[] {0x02};
    final var bits = new DERBitString(aut);

    when(infosParser.readObject()).thenReturn(setParser).thenReturn(null);
    when(setParser.readObject()).thenReturn(octets).thenReturn(bits).thenReturn(null);

    // when / then
    assertThatThrownBy(() -> sut.parseAll(mock(InputStream.class), SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("| Missing fields in egkInfo");
  }

  @Test
  void parseAllThrowsWhenMissingCvcHash() throws Exception {
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
    final byte[] aut = new byte[] {0x02};
    final var bits = new DERBitString(aut);

    when(infosParser.readObject()).thenReturn(setParser).thenReturn(null);
    when(setParser.readObject()).thenReturn(utcTime).thenReturn(bits).thenReturn(null);

    // when / then
    assertThatThrownBy(() -> sut.parseAll(mock(InputStream.class), SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("| Missing fields in egkInfo");
  }

  @Test
  void parseAllThrowsWhenMissingAutHash() throws Exception {
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
    final byte[] cvc = new byte[] {0x01};
    final var octets = new DEROctetString(cvc);

    when(infosParser.readObject()).thenReturn(setParser).thenReturn(null);
    when(setParser.readObject()).thenReturn(utcTime).thenReturn(octets).thenReturn(null);

    // when / then
    assertThatThrownBy(() -> sut.parseAll(mock(InputStream.class), SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("| Missing fields in egkInfo");
  }

  @Test
  void parseAllThrowsWhenIOExceptionDuringParsing() throws Exception {
    // given
    final var contentParser = mock(org.bouncycastle.asn1.ASN1StreamParser.class);
    when(asn1StreamParserFactory.create(any(InputStream.class))).thenReturn(contentParser);
    when(contentParser.readObject()).thenThrow(new IOException("Test IO error"));

    // when / then
    assertThatThrownBy(() -> sut.parseAll(mock(InputStream.class), SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("Error streaming CMS eContent parse");
  }

  @Test
  void parseAllThrowsWhenParseExceptionDuringParsing() throws Exception {
    // given
    final var contentParser = mock(org.bouncycastle.asn1.ASN1StreamParser.class);
    when(asn1StreamParserFactory.create(any(InputStream.class))).thenReturn(contentParser);

    final var seqParser = mock(ASN1SequenceParser.class);
    final var dummyEnc = mock(ASN1Encodable.class);
    final var infosParser = mock(ASN1SequenceParser.class);
    final var setParser = mock(ASN1SetParser.class);
    final var invalidUtcTime = mock(ASN1UTCTime.class);
    final var octets = new DEROctetString(new byte[] {0x01});
    final var bits = new DERBitString(new byte[] {0x02});

    when(contentParser.readObject()).thenReturn(seqParser);
    when(seqParser.readObject()).thenReturn(dummyEnc).thenReturn(infosParser);
    when(infosParser.readObject()).thenReturn(setParser).thenReturn(null);
    when(invalidUtcTime.toASN1Primitive()).thenReturn(invalidUtcTime);
    when(invalidUtcTime.getDate()).thenThrow(new ParseException("Invalid ASN.1", 0));
    when(setParser.readObject())
        .thenReturn(invalidUtcTime)
        .thenReturn(octets)
        .thenReturn(bits)
        .thenReturn(null);

    // when / then
    assertThatThrownBy(() -> sut.parseAll(mock(InputStream.class), SESSION_ID))
        .isInstanceOf(ImportDataException.class)
        .hasMessageContaining("Error streaming CMS eContent parse");
  }

  @Test
  void parseAllHandlesFieldsInDifferentOrder() throws Exception {
    // given - Fields in reverse order (bits, octets, utcTime)
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

    final var utcTime = new ASN1UTCTime("230101120000Z");
    final byte[] cvc = new byte[] {0x0A, 0x0B};
    final var octets = new DEROctetString(cvc);
    final byte[] aut = new byte[] {0x0C, 0x0D};
    final var bits = new DERBitString(aut);

    // Reading in reverse order
    when(setParser.readObject())
        .thenReturn(bits)
        .thenReturn(octets)
        .thenReturn(utcTime)
        .thenReturn(null);

    // when
    final List<EgkTransferEntry> result = sut.parseAll(mock(InputStream.class), SESSION_ID);

    // then
    assertThat(result)
        .hasSize(1)
        .allSatisfy(
            entry -> {
              assertThat(entry.getCvcHash()).isEqualTo(cvc);
              assertThat(entry.getAutHash()).isEqualTo(aut);
              final var expectedLocal =
                  LocalDateTime.ofInstant(
                      utcTime.getDate().toInstant(), ZoneId.of("Europe/Berlin"));
              assertThat(entry.getNotAfter()).isEqualTo(expectedLocal);
            });
  }

  @Test
  void parseAllIgnoresUnexpectedElements() throws Exception {
    // given - include unexpected element types
    final var setParser = mock(ASN1SetParser.class);

    final var utcTime = new ASN1UTCTime("230101120000Z");
    final byte[] cvc = new byte[] {0x01};
    final var octets = new DEROctetString(cvc);
    final byte[] aut = new byte[] {0x02};
    final var bits = new DERBitString(aut);
    final var unexpectedEnc = new ASN1Integer(1);

    when(setParser.readObject())
        .thenReturn(utcTime)
        .thenReturn(octets)
        .thenReturn(unexpectedEnc) // unexpected element
        .thenReturn(bits)
        .thenReturn(null);

    // when
    final EgkTransferEntry result =
        ReflectionTestUtils.invokeMethod(sut, "parseSingleEgkInfo", setParser, SESSION_ID);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getCvcHash()).isEqualTo(cvc);
    assertThat(result.getAutHash()).isEqualTo(aut);
  }

  @Test
  void parseAllReturnsEmptyListWhenNoEntries() throws Exception {
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

    // No entries in infosParser
    when(infosParser.readObject()).thenReturn(null);

    // when
    final List<EgkTransferEntry> result = sut.parseAll(mock(InputStream.class), SESSION_ID);

    // then
    assertThat(result).isEmpty();
  }
}
