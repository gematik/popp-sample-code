# Anforderungsabgleiche für PoPP-Service und Online-Check-in

Die beiden Excel-Dateien vergleichen Anforderungen aus gematik-Spezifikationen
mit der Implementierung in diesem Repository. Sie zeigen, welche Funktionen
bereits vorhanden, nur teilweise umgesetzt oder noch offen sind und welche
Nachweise außerhalb des Repositories erbracht werden müssen.

Damit dienen sie als Arbeitsgrundlage für Entwicklung, Review, Testplanung und
die Abstimmung mit Herstellern und Betreibern. **Sie sind weder eine
Zulassungsbescheinigung noch ein Nachweis vollständiger Spezifikationskonformität.**

## Welche Datei ist wofür gedacht?

| Datei | Gegenstand | Umfang |
|---|---|---|
| [gemF_PoPP_Online_Check-in_1.0.0_CC4_requirements.xlsx](gemF_PoPP_Online_Check-in_1.0.0_CC4_requirements.xlsx) | Online-Check-in mit GesundheitsID nach dem Entwurf `gemF_PoPP_Online_Check-in_1.0.0_CC4.pdf` (Stufe 2), unter anderem Check-in, Tokenabruf, Statusabfrage und QR-Code. | 51 Anforderungen und Akzeptanzkriterien; drei Anwendungsfälle zusätzlich im Blatt `Scope`. |
| [gemSpec_PoPP_Service_V1.1.0_requirements.xlsx](gemSpec_PoPP_Service_V1.1.0_requirements.xlsx) | Freigegebene PoPP-Service-Spezifikation V1.1.0 (Stufe 1): eGK-Verarbeitung, Token, Hashdatenbank und Import, ZETA, Zertifikate, VAU/HSM und Betrieb. | 146 Anforderungen und Akzeptanzkriterien; drei Anwendungsfälle zusätzlich im Blatt `Scope`. |

Die Dateien bewerten **unterschiedliche Spezifikationsstände und Funktionsbereiche**.
Sie sind keine zwei Versionen derselben Checkliste. Ein Status aus einer Datei
darf deshalb nicht ungeprüft in die andere übernommen werden. Insbesondere
sind neue CC4-Pflichten nicht automatisch Anforderungen der Service-Version 1.1.0.

## So lassen sich die Tabellen nutzen

1. Mit `Summary` beginnen: Dort stehen Ergebnis, Spezifikationsbasis, analysierter
   Codestand und die wichtigsten Lücken.
2. In `Requirements` nach Status, Anforderungs-ID oder Verantwortlichkeit filtern.
   Für die Umsetzungsplanung vor allem `Open` und `Partial` betrachten.
3. `Implementation`, `Gap / follow-up` und `Depends on` gemeinsam lesen.
   Zugehörige Codefundstellen über die Beleg-IDs in `Evidence` nachvollziehen.
4. Vor einer Umsetzung oder Statusänderung die Originalanforderung auf der
   angegebenen PDF-Seite sowie die Abgrenzungen in `Scope` prüfen. Für externe
   Pflichten die benötigten Betriebs-, Beschaffungs- oder Gutachtennachweise einholen.

## Aufbau beider Arbeitsmappen

| Tabellenblatt | Inhalt |
|---|---|
| `Requirements` | Einzelbewertung mit Anforderungs-ID, Kurzbeschreibung, Kapitel/PDF-Seite, Verbindlichkeit, Verantwortlichkeit, Status, Implementierung, Restpunkten und Abhängigkeiten. |
| `Legend` | Bedeutung der Statuswerte und Bewertungsregeln. |
| `Summary` | Gesamteinordnung, Analysegrundlage, Codestand, Statuszahlen und wesentliche Lücken. |
| `Evidence` | Code-/Konfigurations- und Testfundstellen mit Beleg-ID, relativem Dateipfad, Zeilenbereich, Aussagegrenze und SHA256-Dateihash. |
| `Performance` | Leistungs-, Mengen- und Zeitvorgaben sowie fehlende Nachweise. Extern referenzierte Vorgaben sind als solche gekennzeichnet; Zahlen sind keine gemessenen Istwerte. |
| `Scope` | Betrachteter Umfang, Anwendungsfälle, Versionsabgrenzungen, externe Komponenten und Grenzen der Bewertung. |

Die Titel und Erläuterungen sind eigene Kurzfassungen. Maßgeblich bleibt der
Originaltext der jeweils genannten Spezifikation. `PDF S.` bezeichnet die
physische Seite der PDF-Datei, nicht zwingend eine abweichende gedruckte Seitenzahl.

## Was bedeuten die Statuswerte?

| Status | Bedeutung |
|---|---|
| `Done` | Die konkret beschriebene Codefunktion ist vorhanden und belegt. Das bestätigt nicht automatisch den vollständigen Anwendungsfall oder den produktiven Betrieb. |
| `Partial` | Eine wesentliche Teilfunktion ist vorhanden, aber mindestens eine geforderte Funktion oder ein erforderlicher Nachweis fehlt. |
| `Open` | Die geforderte technische Funktion oder der erforderliche technische Nachweis wurde im untersuchten Projekt nicht gefunden. |
| `Not assessed / external` | Die Pflicht ist relevant, ihr tatsächlicher Erfüllungsstand aber aus dem Repository nicht entscheidbar, etwa bei Betriebsorganisation oder Zertifikatsbeschaffung. |
| `Not applicable / external` | Die Anforderung adressiert ein anderes Produkt oder einen anderen Hersteller, beispielsweise eine mobile App oder einen eGK-Lieferanten. |
| `Not applicable / optional` | Eine ausdrückliche KANN-Funktion wird nicht genutzt; daraus entsteht keine eigene Pflichtlücke. |
| `Not applicable / conditional` | Die Pflicht gilt erst bei einer bestimmten, aktuell nicht gewählten oder implementierten Variante. Bei deren Einführung muss sie erneut bewertet werden. |

**Externe, optionale oder bedingte Positionen sind keine erledigten Anforderungen.**
Fehlende verpflichtende VAU-/HSM-Funktionen werden auch bei einer
Referenzimplementierung nicht pauschal als optional eingestuft.

Eine einzelne APDU- oder JWT-Erzeugungsfunktion kann `Done` sein, während die
übergeordnete Authentisierung noch `Partial` oder `Open` ist. Anforderungen und
Akzeptanzkriterien überlappen sich; aus den Statuszahlen lässt sich daher keine
belastbare prozentuale Gesamterfüllung ableiten.

## Aussagekraft und Grenzen

Die Auswertungen sind **Momentaufnahmen vom 30.10.2026**. Bewertet wurde die
damalige lokale Arbeitskopie einschließlich vorhandener Änderungen, nicht
ausschließlich der Git-Commit. Den genauen Stand und die PDF-Identifikation
enthält jeweils `Summary`.

Grundlage war ein statischer Abgleich von Spezifikation, Quellcode,
Datenbankschema, Konfiguration und ausgewähltem Testcode. Referenzierte Tests
wurden gelesen, **für diesen Abgleich aber nicht ausgeführt**.
Die Tabellen bestätigen somit keine erfolgreichen Echtkarten-, Integrations-,
Last-, Sicherheits- oder Zulassungstests und keinen Livebetrieb.

Weitere normative Dokumente und externe Schnittstellenspezifikationen wurden
nicht vollständig eigenständig mitgeprüft. Ebenso bedeutet ein fehlender
Nachweis im Repository nicht, dass eine externe organisatorische Pflicht
tatsächlich unerfüllt ist. Die jeweilige Aussagegrenze steht in der Bewertung.

## Belege nachvollziehen und Bewertungen pflegen

`E...`-Belege verweisen auf Code oder Konfiguration; `T...`-Belege auf vorhandenen,
nicht ausgeführten Testcode. Die relativen Pfade in `Evidence` sind vom
Repository-Stamm aus zu lesen. Eingebettete lokale Dateilinks können auf anderen
Arbeitsplätzen ins Leere zeigen; dann den relativen Pfad und Zeilenbereich nutzen.
Nach Codeänderungen können Zeilenangaben und Dateihashes veraltet sein.

Bei Änderungen an Code oder Spezifikation die betroffenen Anforderungen erneut
prüfen und Status, Begründung, Restpunkte, Abhängigkeiten und Belege gemeinsam
aktualisieren. Auch Analysedatum, Codestand und Statuszahlen in `Summary` müssen
dann angepasst werden. **Die Tabellen synchronisieren sich nicht automatisch mit
dem Quellcode; die Zusammenfassungen werden nicht automatisch aus
Statusänderungen neu berechnet.**
