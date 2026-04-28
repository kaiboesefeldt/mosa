## Problem

Wenn ein Topic bereits ausgewählt ist, kann ein neu geposteter Beitrag in einem anderen Topic unmittelbar nach dem Verfassen aus der aktuellen Liste verschwinden. Das wirkt auf Nutzerinnen und Nutzer so, als waere der Beitrag nicht angekommen.

Das ist besonders kritisch im MVP, weil es den zentralen Content-Loop betrifft: Schreiben, Abschicken, den eigenen Beitrag wiedersehen. Wenn dieser Moment unsicher wirkt, verliert die Anwendung Vertrauen.

## Goal

Nach dem Veröffentlichen soll Mosa den Nutzer sichtbar zum gerade erstellten Post zurueckfuehren. Der Publish-Moment soll sich wie ein abgeschlossener, bestaetigter Vorgang anfuehlen, nicht wie eine Suche in einer gefilterten Liste.

Produktkonzept:

- Die bisherige Topic-Auswahl soll als eine Form von Filter oder Sicht verstanden werden, nicht als einziger moeglicher Navigationszustand.
- Topics bleiben semantische Filter: Sie zeigen Posts, die zur Topic-Beschreibung passen.
- Eine Sicht "Meine Beitraege" soll alle eigenen Posts zeigen, sinnvollerweise sortiert nach Absendedatum.
- Nach erfolgreichem Publish bekommt der neue Post Vorrang vor dem vorher aktiven Topic-Filter.
- Wenn der neue Post im aktuell gewaehlten Topic sichtbar ist, kann Mosa in diesem Topic bleiben und den Post dort auswaehlen.
- Wenn der neue Post im aktuell gewaehlten Topic nicht sichtbar waere, soll Mosa in eine passende Sicht wie "Meine Beitraege" wechseln und den neuen Post dort auswaehlen.
- Von einem eigenen Post aus soll es perspektivisch naheliegend sein, aehnliche Beitraege zu entdecken, ohne dafuer ein temporaeres Topic zu erzeugen.
- Der Nutzer soll nach dem Publish verstehen: "Mein Beitrag ist da, und ich sehe ihn jetzt."

Dieses Konzept ist die derzeit bevorzugte Produkt-Richtung fuer den MVP, aber noch nicht als finale Detailentscheidung zu verstehen.

## Constraints

- Der aktuelle Topic-Zustand darf nicht dazu fuehren, dass der gerade veroeffentlichte Post fuer die schreibende Person scheinbar verloren geht.
- Der Composer darf Nutzerinnen und Nutzer nicht implizit zwingen, nur in das aktuell ausgewählte Topic zu schreiben.
- Der Publish-Moment soll eine eindeutige Erfolgserfahrung haben: Der neue Post ist sichtbar, ausgewählt oder klar erreichbar.
- Ein vorher aktiver Topic-Filter darf nicht still und erklaerungslos gegen die Sichtbarkeit des neuen Posts arbeiten.
- Die Loesung darf die Semantik einer Topic-Postliste nicht brechen, indem fremde Posts kommentarlos in eine Topic-Sicht gemischt werden.
- Die Loesung soll die bestehende Topic-Navigation zu einem verstaendlichen Filter- oder Sichtenmodell erweitern, statt temporaere Topics als Workaround einzufuehren.
- Das Verhalten soll im MVP eindeutig und konsistent sein.

## Expected Outcome

- Ein frisch veroeffentlichter Post ist fuer die schreibende Person unmittelbar sichtbar oder wird automatisch in einen sichtbaren Kontext gebracht.
- Wenn vor dem Publish ein anderes Topic aktiv war, ist klar erkennbar, was mit diesem Lesekontext passiert ist.
- Nutzerinnen und Nutzer koennen eine Sicht auf ihre eigenen Beitraege erreichen und dort neu veroeffentlichte Posts wiederfinden.
- Nutzerinnen und Nutzer koennen nach dem Publish ohne Nachdenken weiterlesen, antworten, den Post kopieren oder weiter navigieren.
- Ein aktiver Topic-Filter erzeugt keinen irrefuehrenden "Post weg" Eindruck.
