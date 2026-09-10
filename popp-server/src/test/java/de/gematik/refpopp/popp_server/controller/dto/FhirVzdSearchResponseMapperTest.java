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

package de.gematik.refpopp.popp_server.controller.dto;

import static org.junit.jupiter.api.Assertions.*;

import de.gematik.refpopp.popp_server.vzd.dto.VzdAddress;
import de.gematik.refpopp.popp_server.vzd.dto.VzdEntry;
import de.gematik.refpopp.popp_server.vzd.dto.VzdLocation;
import de.gematik.refpopp.popp_server.vzd.dto.VzdSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class FhirVzdSearchResponseMapperTest {

  @Test
  void mapsSingleEntryCorrectly() {
    var address = new VzdAddress("Hauptbahnhofstraße 1", "12345", "Musterstadt");
    var entry =
        new VzdEntry(
            "1-SMC-B-Testkarte--883110000168757",
            "123456789",
            "Apotheke am Hauptbahnhof",
            List.of("01234 567890"),
            address,
            null);

    var result = new VzdSearchResult(1, List.of(entry), null);

    var response = FhirVzdSearchResponseMapper.toResponse(result);

    assertNotNull(response);
    assertEquals(1, response.practitioners().size());
    var item = response.practitioners().getFirst();
    assertEquals("Apotheke am Hauptbahnhof", item.name());
    assertEquals("1-SMC-B-Testkarte--883110000168757", item.telematikId());
    assertEquals("123456789", item.iknr());
    assertEquals("Hauptbahnhofstraße 1, 12345 Musterstadt", item.address());
    assertEquals("Telefon: 01234 567890", item.contact());
  }

  @Test
  void returnsNullWhenNoEntries() {
    var result = new VzdSearchResult(0, List.of(), null);
    var response = FhirVzdSearchResponseMapper.toResponse(result);
    assertNull(response);
  }

  @Test
  void usesFirstEntryWhenMultiple() {
    var address1 = new VzdAddress("Strasse 1", "11111", "Ort1");
    var entry1 =
        new VzdEntry(
            "telem1", "111111111", "Org1", List.of("0001"), address1, new VzdLocation(1.0, 2.0));

    var address2 = new VzdAddress("Strasse 2", "22222", "Ort2");
    var entry2 =
        new VzdEntry(
            "telem2", "222222222", "Org2", List.of("0002"), address2, new VzdLocation(3.0, 4.0));

    var result = new VzdSearchResult(2, List.of(entry1, entry2), null);
    var response = FhirVzdSearchResponseMapper.toResponse(result);

    assertNotNull(response);
    assertEquals(2, response.practitioners().size());
    assertEquals("Org1", response.practitioners().getFirst().name());
    assertEquals("telem1", response.practitioners().get(0).telematikId());
    assertEquals("111111111", response.practitioners().get(0).iknr());
    assertEquals("Org2", response.practitioners().get(1).name());
  }

  @Test
  void returnsNullForNullResultOrNullEntry() {
    // null overall result
    assertNull(FhirVzdSearchResponseMapper.toResponse(null));

    assertNull(FhirVzdSearchResponseMapper.toResponse(new VzdSearchResult(1, null, null)));
  }

  @Test
  void handlesBlankAddressAndPhoneVariants() {
    // address parts blank -> address should be null
    var blankAddress = new VzdAddress("", " ", null);
    var entryBlankAddress =
        new VzdEntry("TID-blank", "000000000", "BlankOrg", List.of(), blankAddress, null);
    var respBlankAddr =
        FhirVzdSearchResponseMapper.toResponse(
            new VzdSearchResult(1, List.of(entryBlankAddress), null));
    assertNotNull(respBlankAddr);
    assertNull(respBlankAddr.practitioners().getFirst().address());

    // phone numbers null -> contact null
    var entryNullPhones = new VzdEntry("TID-nophones", "000000001", "NoPhoneOrg", null, null, null);
    var respNullPhones =
        FhirVzdSearchResponseMapper.toResponse(
            new VzdSearchResult(1, List.of(entryNullPhones), null));
    assertNotNull(respNullPhones);
    assertNull(respNullPhones.practitioners().getFirst().contact());

    // phone numbers empty or blank-only -> contact null
    var entryBlankPhones =
        new VzdEntry("TID-blankphones", "000000002", "BlankPhoneOrg", List.of("", " "), null, null);
    var respBlankPhones =
        FhirVzdSearchResponseMapper.toResponse(
            new VzdSearchResult(1, List.of(entryBlankPhones), null));
    assertNotNull(respBlankPhones);
    assertNull(respBlankPhones.practitioners().getFirst().contact());

    // mix of blank and valid phone numbers -> only valid ones joined
    var entryPhones =
        new VzdEntry(
            "TID-phones",
            "000000003",
            "PhoneOrg",
            List.of("0123", " ", "4567"),
            new VzdAddress("Baker Street 7", "40404", "London"),
            null);
    var respPhones =
        FhirVzdSearchResponseMapper.toResponse(new VzdSearchResult(1, List.of(entryPhones), null));
    assertNotNull(respPhones);
    assertEquals("Baker Street 7, 40404 London", respPhones.practitioners().getFirst().address());
    assertEquals("Telefon: 0123, 4567", respPhones.practitioners().getFirst().contact());
  }
}
