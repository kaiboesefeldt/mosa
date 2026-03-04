# Next Task: Inline-Vergleich für markierte Textstellen in ClearTerms

## Ziel
Für markierte Textstellen (gelb/rot) soll der Nutzer direkt im Dokument sehen, wie sich der aktuelle Text gegenüber der ursprünglichen Version unterscheidet.

Der Vergleich wird inline unterhalb der betroffenen Stelle eingeblendet. Es wird kein weiteres Popover geöffnet.

## Scope und Constraints
1. Verglichen werden genau zwei Versionen:
- unkritischer Text (siehe Begriffsdefinition unten)
- aktueller Text (effektiv gerenderter Text)

2. Es gibt keine zusätzlichen Begründungsdaten und keine juristischen Erklärungen.

3. Die Datenschutzansicht läuft bereits in einem Dialog/Overlay. Es gibt keine Dialog-in-Dialog- oder Popover-in-Popover-Lösung.

4. Fokus sind kurze Textblöcke. Lange Blöcke müssen funktional korrekt bleiben.

5. Diese Aufgabe gilt generell für ClearTerms-Dokumente und wird anhand des Privacy-Dokuments umgesetzt.

## Begriffe
- Markierte Stelle: Text mit Severity gelb oder rot.
- Betroffener Block: `ClearTermsRenderedBlock` mit `id`.
- Unkritischer Text eines Blocks: Blockzustand entlang der Profilkette, bis einschließlich des letzten Zustands ohne gelbe/rote Markierung dieses Blocks.
- Sobald der Block in der Kette erstmals gelb oder rot markiert ist, wird der unmittelbar davor liegende Zustand als unkritischer Text verwendet.
- Vergleichsbox: Inline-Bereich direkt unter dem betroffenen Block mit unkritischen Text des Blocks.

## Daten- und Mapping-Regeln
1. Klick/Tap-Ziel ist ein markiertes Segment. Der Vergleich bezieht sich immer auf den gesamten betroffenen Block.

2. Jeder interaktive Marker im DOM muss den Block eindeutig referenzieren:
- Pflicht: `data-ct-block-id="<blockId>"`

3. Der Text der Vergleichsbox ist der gerenderte unkritische Text desselben `blockId` in derselben Sprache.

4. Wenn für einen markierten Block der unkritische Text unaufgelöste Platzhalter enthält, so werden diese mit "..." gerendert.

## Interaktionsregeln
1. Klick/Tap auf markierte Stelle:
- aktueller Text bleibt unverändert sichtbar
- Vergleichsbox wird direkt unter diesem Block ein-/ausgeblendet
- Für Blöcke mit `inlineWithNext = true` wird das Inline-Paar als ein gemeinsamer Interaktionsblock behandelt.
- Die Vergleichsbox wird unterhalb des gesamten Inline-Paares angezeigt.

2. Es darf maximal eine Vergleichsbox gleichzeitig geöffnet sein.
- Öffnen einer neuen Vergleichsbox schließt die vorher geöffnete.
- Klick auf dieselbe Stelle schließt die aktuell geöffnete Vergleichsbox.

3. Verhalten ist auf Desktop und Mobile identisch.

4. Die Vergleichsbox zeigt:
- Label `Original` als lokalisierten UI-Text (mindestens `de` und `en`).
- Unkritischen Text des betroffenen Blocks
- kein Token-/Wortdiff

## Rail-Regeln (Mobile)
1. Mobile hat eine dauerhaft sichtbare vertikale Rail innerhalb des Legal-Dialogs.
- Mobile ist definiert als Viewport `max-width: 900px`.

2. Die Rail ist rein visuell. Kein Tap, kein Jump-to-Position.

3. Markerpositionen sind proportional zur Scrollhöhe des Dokument-Contents.

4. Kollisionsregel:
- Marker werden auf Rail-Pixelposition quantisiert (`round(topPercent * railHeightPx / 100)`).
- Fallen gelb und rot in denselben quantisierten Pixel-Bucket, wird nur rot dargestellt.

5. Die Rail zeigt zusätzlich einen dezenten Viewport-Indikator:
- Position entspricht aktueller `scrollTop / (scrollHeight - clientHeight)`.
- Größe entspricht `clientHeight / scrollHeight` (mit sinnvoller Mindesthöhe für Sichtbarkeit).
- Randfall: Wenn `scrollHeight <= clientHeight`, ist der Indikator auf voller Rail-Höhe mit Position `top = 0`.

## Nicht Teil dieser Aufgabe
- Filter/Übersichten wie `nur rote`, `x von y`, Clustering
- Komplexe Diff-Visualisierung mit granularer Token-Hervorhebung
- Fachliche `Warum`-Begründungen über den reinen Textvergleich hinaus
- Interaktive Rail-Navigation

## Akzeptanzkriterien (testbar)
1. Nutzer kann auf gelb/rot markierten Text klicken/tappen und sieht inline darunter den unkritischen Text des betroffenen Blocks.
2. Beim Vergleich wird kein zusätzliches Popover/kein zusätzlicher Dialog geöffnet.
3. Der aktuelle Text bleibt stets sichtbar.
4. Zu jedem Zeitpunkt ist höchstens eine Vergleichsbox geöffnet.
5. Desktop und Mobile zeigen dasselbe Expand/Collapse-Verhalten.
6. Auf Mobile ist die Rail dauerhaft sichtbar, inklusive Marker und Viewport-Indikator.
7. Bei Marker-Kollision in derselben Rail-Position wird rot dargestellt.
