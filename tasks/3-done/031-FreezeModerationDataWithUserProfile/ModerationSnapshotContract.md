Ziel dieses Dokuments ist, die fachliche und technische Semantik des Freeze und Restore fuer profilbezogene Moderationsdaten eindeutig festzulegen.

## Vertragsentscheidungen

### 1. Umfang des Freeze

Ein Freeze eines Profils umfasst neben den bereits vorhandenen Inhaltsdaten saemtliche pod-lokalen Moderationsdaten, die fachlich zu diesem Profil gehoeren.

Dazu gehoeren:

- alle Beschwerden, die von diesem Profil eingereicht wurden
- alle Beschwerden ueber Beitraege oder Antworten, die dieses Profil erstellt hat
- alle Admin-Nachrichten, die diesem Profil als Empfaenger zugeordnet sind
- alle Admin-Nachrichten, die diesem Profil als Sender zugeordnet sind

Der Umfang ist absichtlich symmetrisch zur bereits festgelegten Loesch-Semantik aus Task 030 geschnitten, damit `Loeschen` und `Freeze` dieselbe fachliche Zuordnung eines Profils verwenden.

### 2. Snapshot-Vertrag fuer Moderationsdaten

Der Snapshot darf nicht bei einem reinen Inhaltsrecord bleiben. Er braucht eine explizite Typunterscheidung zwischen:

- Inhaltsrecord
- Beschwerde-Record
- Admin-Nachrichten-Record

Fuer Moderationsdaten werden physische Snapshots der fachlich relevanten Datensaetze verwendet, keine indirekten Referenzen auf den Live-Zustand des Pods.

Jeder snapshotte Moderationsrecord muss mindestens enthalten:

- einen stabilen Typ
- die originale fachliche ID
- alle Felder, die fuer eine spaetere vollstaendige Rekonstruktion des Datensatzes noetig sind
- die Profilzuordnung, aus der die Aufnahme in den Freeze fachlich hergeleitet wurde

Fuer Beschwerden bedeutet das mindestens die heute bereits persistierten Felder aus `PostComplaintRecord`, inklusive Entscheidungsdaten, falls die Beschwerde bereits bearbeitet wurde.

Fuer Admin-Nachrichten bedeutet das mindestens die heute bereits persistierten Felder aus `AdminMessageRecord`.

### 3. Restore-Semantik

Ein Restore spielt alle im Snapshot enthaltenen Moderationsdaten wieder in die produktiven Moderationsspeicher des Pods ein. Restore ist damit keine reine Anzeigehilfe, sondern eine echte Wiederherstellung des eingefrorenen Pod-Zustands fuer dieses Profil.

Das Restore gilt fachlich nur dann als erfolgreich, wenn die wiederhergestellten Moderationsdaten anschliessend ueber die regulaeren Read-Pfade verfuegbar sind.

### 4. Ungelesen-Semantik nach Restore

Alle durch den Restore wieder eingespielten Admin-Nachrichten gelten fachlich wieder als ungelesen.

Alle durch den Restore wieder eingespielten Beschwerden gelten fachlich wieder als ungelesen.

Fuer Admin-Nachrichten bedeutet das konkret:

- sie duerfen nach dem Restore nicht als bereits gesehen markiert sein
- bestehende Mechanismen fuer lokale Inbox-Zustaende muessen wiederhergestellte Nachrichten deshalb wie neue, noch nicht gesehene Nachrichten behandeln

Fuer Beschwerden bedeutet das konkret:

- es gibt heute noch keinen expliziten serverseitigen Lesestatus fuer Beschwerden
- die Umsetzung darf und soll deshalb eine zulaessige Schema- oder Persistenzerweiterung einfuehren, damit `ungelesen` fuer wiederhergestellte Beschwerden eindeutig und testbar modelliert werden kann
- falls die bestehende Beschwerdeoberflaeche heute nur zwischen `offen` und `bearbeitet` unterscheidet, reicht das fuer diese Task nicht aus; `ungelesen nach Restore` braucht einen eigenen fachlich nachvollziehbaren Zustand oder eine gleichwertige persistierte Ableitung

### 5. Konflikt-Semantik beim Restore

Der Restore soll keinen unklaren Merge auf bestehende Live-Daten ausfuehren.

Deshalb gilt fuer Konflikte mit bereits vorhandenen IDs:

- trifft das Restore auf eine bereits vorhandene ID desselben Snapshot-Typs, wird das bestehende pod-lokale Element durch den Snapshot-Zustand ersetzt
- eine implizite Feld-fuer-Feld-Verschmelzung findet nicht statt
- falls technische Randbedingungen ein Ersetzen mit identischer ID verhindern, ist eine explizite, konsistente Ersatzstrategie zulaessig, solange das Ergebnis fachlich dem Snapshot-Zustand entspricht und automatisiert verifizierbar ist

Die spaetere Implementierung soll diese Ersetzungssemantik durch Tests absichern.

### 6. Erlaubte Schema-Anpassungen

Schema-Anpassungen an Moderations- und Snapshot-Speichern sind ausdruecklich zulaessig, wenn sie fuer einen effizienten und konsistenten Freeze und Restore erforderlich sind.

Das schliesst insbesondere ein:

- zusaetzliche Snapshot-Typen oder Snapshot-Felder
- zusaetzliche persistierte Zustandsfelder fuer `ungelesen`
- Hilfsstrukturen, die eine effiziente Selektion oder ein effizientes Restore erlauben

Nicht Ziel dieses Vertrags ist eine spekulative Generalisierung fuer beliebige kuenftige Snapshot-Daten. Die Erweiterung soll minimal bleiben und nur den jetzt benoetigten Moderationsumfang sauber tragen.
