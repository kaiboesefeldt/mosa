# Zielstruktur fuer ClearTerms-Namen und -Pakete

## Status

Dieses Dokument ist der verbindliche Vorschlag aus `sub001`. Die nachfolgenden Umsetzungs-Subtasks verwenden diese Matrix als Soll-Zustand fuer `shared`, `server`, `client` und die dazugehoerigen Tests.

## Leitentscheidungen

- Das Praefix `ClearTerms` wird innerhalb des Paketbaums `straightway.mosa.legal.clearterms...` durchgaengig entfernt.
- Das Wurzelpaket `straightway.mosa.legal.clearterms` behaelt nur bewusst benannte oeffentliche Einstiegspunkte.
- Oeffentliche Einstiegspunkte duerfen als seltene Ausnahme das Praefix `ClearTerms` behalten, wenn der Name ausserhalb des Pakets sonst zu generisch waere.
- Dokumentmodelle werden unter `clearterms.document` gebuendelt.
- Wert- und Formatierungstypen werden unter `clearterms.value` gebuendelt.
- Rendering-spezifische Ergebnis- und Hilfstypen werden unter `clearterms.render` gebuendelt.
- Plattform- und Infrastrukturabstraktionen werden unter `clearterms.spi` gebuendelt.
- Serverseitige YAML-Integration liegt unter `server ... clearterms.yaml`.
- Clientseitige Browser-Implementierungen liegen unter `client ... clearterms.spi.browser` beziehungsweise `client ... clearterms.render.browser`.

## Kritische Benennungsentscheidungen

- `document` statt `model`: `Baseline`, `Profile`, `Reference` und `TextBlock` beschreiben konkrete ClearTerms-Dokumentartefakte, keine beliebigen Laufzeitmodelle.
- `value` statt Verbleib im Wurzelpaket: Kurze Namen wie `Value`, `NumberFormat` und `TimeValue` werden erst im engeren Paketkontext wirklich lesbar.
- `spi` statt `support`: `HashProvider`, `TextEncoder` und `ResourceReader` sind austauschbare technische Ports, nicht fachliche Hilfsklassen.
- `render` enthaelt auch `RenderedBlock`, `RenderedLanguage` und `RenderBackground`, weil diese Typen ausschliesslich im Kontext des Renderings entstehen und genutzt werden.
- `ClearTermsRenderer` bleibt als einzige bewusste Praefix-Ausnahme im Wurzelpaket, weil er der zentrale oeffentliche Einstiegspunkt der API ist und `Renderer` ausserhalb des Pakets zu generisch waere.

## Ausnahmen

- `ClearTermsRenderer` behaelt sein Praefix als oeffentlicher API-Einstiegspunkt.
- Es ist keine weitere Beibehaltung des Praefixes `ClearTerms` vorgesehen.
- Es ist keine zweite oeffentliche Sammelstelle im Wurzelpaket neben `ClearTermsRenderer` vorgesehen.
- Lange Namen bleiben nur dort erhalten, wo sie fachlich unterscheiden muessen, zum Beispiel `ProfileChainCompletenessChecker` und `YamlCanonicalJsonConverter`.

## Zielmatrix Shared

| Heute | Zielname | Zielpaket |
| --- | --- | --- |
| `ClearTermsRenderer` | `ClearTermsRenderer` | `straightway.mosa.legal.clearterms` |
| `ClearTermsBaseline` | `Baseline` | `straightway.mosa.legal.clearterms.document` |
| `ClearTermsProfile` | `Profile` | `straightway.mosa.legal.clearterms.document` |
| `ClearTermsReference` | `Reference` | `straightway.mosa.legal.clearterms.document` |
| `ClearTermsTextBlock` | `TextBlock` | `straightway.mosa.legal.clearterms.document` |
| `ClearTermsTextBlockHints` | `TextBlockHints` | `straightway.mosa.legal.clearterms.document` |
| `ClearTermsTextOverrideSeverity` | `TextOverrideSeverity` | `straightway.mosa.legal.clearterms.document` |
| `ClearTermsValue` | `Value` | `straightway.mosa.legal.clearterms.value` |
| `ClearTermsNumberFormat` | `NumberFormat` | `straightway.mosa.legal.clearterms.value` |
| `ClearTermsNumberGrouping` | `NumberGrouping` | `straightway.mosa.legal.clearterms.value` |
| `ClearTermsNumberOptions` | `NumberOptions` | `straightway.mosa.legal.clearterms.value` |
| `ClearTermsLocale` | `Locale` | `straightway.mosa.legal.clearterms.locale` |
| `ClearTermsLocaleProvider` | `LocaleProvider` | `straightway.mosa.legal.clearterms.locale` |
| `ClearTermsLocaleLoader` | `LocaleLoader` | `straightway.mosa.legal.clearterms.locale` |
| `ClearTermsLocaleRepository` | `LocaleRepository` | `straightway.mosa.legal.clearterms.locale` |
| `ClearTermsJsonNode` | `JsonNode` | `straightway.mosa.legal.clearterms.parse` |
| `ClearTermsJsonObjectContract` | `JsonObjectContract` | `straightway.mosa.legal.clearterms.parse` |
| `ClearTermsJsonParser` | `JsonParser` | `straightway.mosa.legal.clearterms.parse` |
| `ClearTermsJsonValueReader` | `JsonValueReader` | `straightway.mosa.legal.clearterms.parse` |
| `ClearTermsDocumentReader` | `DocumentReader` | `straightway.mosa.legal.clearterms.parse` |
| `ClearTermsScalarStringNormalizer` | `ScalarStringNormalizer` | `straightway.mosa.legal.clearterms.parse` |
| `ClearTermsPlaceholder` | `Placeholder` | `straightway.mosa.legal.clearterms.placeholder` |
| `ClearTermsPlaceholderType` | `PlaceholderType` | `straightway.mosa.legal.clearterms.placeholder` |
| `ClearTermsPlaceholderParser` | `PlaceholderParser` | `straightway.mosa.legal.clearterms.placeholder` |
| `ClearTermsPlaceholderContentParser` | `PlaceholderContentParser` | `straightway.mosa.legal.clearterms.placeholder` |
| `ClearTermsRenderBackground` | `RenderBackground` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsRenderedBlock` | `RenderedBlock` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsRenderedLanguage` | `RenderedLanguage` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsNumberRenderer` | `NumberRenderer` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsDurationTextRenderer` | `DurationTextRenderer` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsTimeTextRenderer` | `TimeTextRenderer` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsTimeValueParser` | `TimeValueParser` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsBlockBackgroundResolver` | `BlockBackgroundResolver` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsTimeZoneDisplayConverter` | `TimeZoneDisplayConverter` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsTimeDisplayValue` | `TimeDisplayValue` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsTimeValue` | `TimeValue` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsValueRenderer` | `ValueRenderer` | `straightway.mosa.legal.clearterms.render` |
| `ClearTermsProfileValidator` | `ProfileValidator` | `straightway.mosa.legal.clearterms.validation` |
| `ClearTermsProfileChainCompletenessChecker` | `ProfileChainCompletenessChecker` | `straightway.mosa.legal.clearterms.validation` |
| `ClearTermsValueTypeValidator` | `ValueTypeValidator` | `straightway.mosa.legal.clearterms.validation` |
| `ClearTermsValidationException` | `ValidationException` | `straightway.mosa.legal.clearterms.validation` |
| `ClearTermsCanonicalStringEscaper` | `CanonicalStringEscaper` | `straightway.mosa.legal.clearterms.canonical` |
| `ClearTermsCanonicalNumberRenderer` | `CanonicalNumberRenderer` | `straightway.mosa.legal.clearterms.canonical` |
| `ClearTermsCanonicalNodeRenderer` | `CanonicalNodeRenderer` | `straightway.mosa.legal.clearterms.canonical` |
| `ClearTermsHashProvider` | `HashProvider` | `straightway.mosa.legal.clearterms.spi` |
| `ClearTermsTextEncoder` | `TextEncoder` | `straightway.mosa.legal.clearterms.spi` |
| `ClearTermsResourceReader` | `ResourceReader` | `straightway.mosa.legal.clearterms.spi` |

## Zielmatrix Server

| Heute | Zielname | Zielpaket |
| --- | --- | --- |
| `ClearTermsYamlCanonicalJsonConverter` | `YamlCanonicalJsonConverter` | `straightway.mosa.legal.clearterms.yaml` |
| `ClearTermsYamlLanguageFragmentMerger` | `YamlLanguageFragmentMerger` | `straightway.mosa.legal.clearterms.yaml` |

## Zielmatrix Client

| Heute | Zielname | Zielpaket |
| --- | --- | --- |
| `ClearTermsBrowserHashProvider` | `BrowserHashProvider` | `straightway.mosa.legal.clearterms.spi.browser` |
| `ClearTermsBrowserTextEncoder` | `BrowserTextEncoder` | `straightway.mosa.legal.clearterms.spi.browser` |
| `JsClearTermsResourceReader` | `BrowserResourceReader` | `straightway.mosa.legal.clearterms.spi.browser` |
| `ClearTermsBrowserTimeZoneDisplayConverter` | `BrowserTimeZoneDisplayConverter` | `straightway.mosa.legal.clearterms.render.browser` |

## Auswirkungen auf die Folge-Subtasks

- `sub002` verschiebt zuerst die Shared-Typen gemaess dieser Matrix und aktualisiert alle Shared-Tests.
- `sub003` zieht die Server-YAML-Integration auf `clearterms.yaml` nach und passt die Imports auf die neuen Shared-Pakete an.
- `sub004` migriert die Client-Integration auf die neuen Shared-Pakete sowie die Browser-Unterpakete.
- `sub005` prueft abschliessend, dass keine alten `ClearTerms*`-Typnamen oder alten Paketlagen unbeabsichtigt uebrig bleiben.
