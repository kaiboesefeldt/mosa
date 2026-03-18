## Zielbild

Der erste Wurf ist eine private pod-lokale Inbox fuer Admin-zu-User-Nachrichten. Der Kanal ist absichtlich unidirektional und klein:

- Admin sendet eine Nachricht an genau ein Empfaengerkonto.
- Das Empfaengerkonto ruft seine Inbox authentifiziert ab.
- Es gibt keine Antwortfunktion.
- Es gibt keine serverseitige Lesebestaetigung im Sinne eines sichtbaren Benutzerstatus.
- Der einzige Lifecycle-Umschlagpunkt ist der erfolgreiche Abruf durch das Empfaengerkonto, bei dem das effektive Ablaufdatum verkuerzt werden kann.
- Admins koennen die Nachricht ueber einen kleinen Compose-Popover absenden, der aus dem bestehenden UI durch Klick auf das Avatar eines Nutzerbeitrags geoeffnet wird.

Die Funktion ist bewusst getrennt vom bestehenden Content-System. Admin-Nachrichten sind weder oeffentliche Posts noch Replies noch Suchinhalte.

## Fachliche Entscheidungen

### 1. Inbox statt Detail-Abruf

Im ersten Wurf soll es nur einen Listenabruf geben:

- `GET /v1/messages`

Ein separater Detail-Endpunkt ist nicht noetig, solange Nachrichten direkt in der Inbox-Liste ausgeliefert werden. Das vereinfacht Lifecycle und Client deutlich.

### 1a. Admin-Compose direkt am Avatar

Die Admin-Sendeaktion soll keinen eigenen grossen Administrationsbereich brauchen. Stattdessen wird sie dort angeboten, wo ein Admin bereits einen Beitrag oder eine Antwort eines Nutzers sieht.

Vorgeschlagene UI-Semantik:

- Ein Admin klickt auf das Avatar des Beitrag- oder Antwortautors.
- Es oeffnet sich ein kleines Popover.
- Das Popover zeigt das Zielkonto implizit ueber den geklickten Beitragskontext an und enthaelt nur die noetigen Eingaben:
  - Betreff
  - Nachrichtentext
  - Senden
  - Abbrechen
- Nach erfolgreichem Senden schliesst sich das Popover.

Nicht-Ziele fuer v1:

- kein separates Admin-Postfach
- keine Antwortfunktion
- keine Vorlagenverwaltung
- keine Mehrfachauswahl von Empfaengern
- keine Dateianhaenge
- keine komplexe Historie direkt im Compose-Popover

### 2. Abruf verkuerzt das Ablaufdatum

Der erfolgreiche authentifizierte Abruf des Empfaengers ist das fachliche Ereignis, das das effektive Ablaufdatum auf hoechstens die kurze TTL ab dem Abruf begrenzt.

Nicht als Abruf zaehlen:

- Erzeugung der Nachricht durch den Admin
- technische Speicherung
- moegliche spaetere Admin-Werkzeuge

Als relevanter Abruf zaehlt:

- erfolgreiche Auslieferung der Nachricht an `GET /v1/messages` fuer genau das Empfaengerkonto

### 3. Zwei TTLs

Es gibt zwei serverseitig konfigurierte Lebensdauern:

- `unretrievedTtl`: z. B. 180 Tage
- `retrievedTtl`: z. B. 28 Tage

Semantik:

- Beim Erzeugen wird `expiresAt = createdAt + unretrievedTtl` gesetzt.
- Beim Abruf wird `expiresAt = minOf(expiresAt, now + retrievedTtl)` gesetzt.

Damit wird die Restlaufzeit beim Abruf nur verkuerzt, aber nie verlaengert. Das ist absichtlich strikt, damit Nachrichten moeglichst schnell verschwinden und zugleich datensparsam nur ein effektives Ablaufdatum gespeichert werden muss.

Beispiel:

- Nachricht erstellt am 1. Januar
- langer Ablauf waere 30. Juni
- Abruf am 10. Februar
- neuer Ablauf ist dann 10. Maerz

Beispiel fuer einen spaeten Abruf:

- langer Ablauf waere 15. Mai
- Abruf am 25. April
- `now + 4 Wochen` waere spaeter als 15. Mai
- `expiresAt` bleibt 15. Mai

### 4. Nachrichten sind immutable

Nachrichteninhalt und Metadaten des Sendens werden nach Erzeugung nicht geaendert.

Mutierbar ist nur das effektive Ablaufdatum `expiresAt`.

## Datenmodell

Minimal vorgeschlagenes Servermodell:

```kotlin
data class AdminMessageRecord(
    val id: String,
    val recipientGid: String,
    val senderGid: String,
    val createdAt: Long,
    val subject: String,
    val body: String,
    val expiresAt: Long,
)
```

Semantik:

- Beim Erzeugen wird `expiresAt = createdAt + unretrievedTtl` gesetzt.
- Beim Abruf wird atomar gesetzt:
  - `expiresAt = minOf(expiresAt, now + retrievedTtl)`

Diese Form ist fuer v1 besser als zusaetzliche Abrufzeitpunkte, weil der Cleanup nur ein Feld beachten muss und keine unnoetigen weiteren Lifecycle-Daten gespeichert werden.

## API-Schnitt

### 1. Nachricht senden

`POST /v1/admin/messages`

Request:

```kotlin
@Serializable
data class CreateAdminMessageRequest(
    val recipientGid: String,
    val subject: String,
    val body: String,
)
```

Verhalten:

- authentifiziert
- nur Admin erlaubt
- `recipientGid` darf nicht leer sein
- `subject` und `body` duerfen nicht leer sein
- bei Erfolg `204 No Content`

Antwort fuer v1:

- leerer Body

Die Empfaenger-GID wird im Admin-Compose-Popover aus dem geklickten Beitragskontext vorbelegt und nicht frei aus einer allgemeinen Nutzersuche gewaehlt.

### 2. Inbox abrufen

`GET /v1/messages`

Antwort:

```kotlin
@Serializable
data class AdminMessage(
    val id: String,
    val senderGid: String,
    val createdAt: Long,
    val subject: String,
    val body: String,
)

@Serializable
data class AdminMessageListResult(
    val entries: List<AdminMessage>,
)
```

Verhalten:

- authentifiziert
- liefert nur Nachrichten fuer das authentifizierte Empfaengerkonto
- vor der Auslieferung werden abgelaufene Nachrichten entfernt
- fuer alle noch sichtbaren Nachrichten wird in derselben Operation `expiresAt` auf hoechstens `now + retrievedTtl` verkuerzt
- Sortierung absteigend nach `createdAt`

## Service-Semantik

Vorgeschlagene API des fachlichen Services:

```kotlin
class AdminMessageService(
    private val store: AdminMessageStore,
    private val clock: Clock,
    private val isAdmin: suspend (String) -> Boolean,
    private val unretrievedTtl: Duration,
    private val retrievedTtl: Duration,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun send(senderGid: String, request: CreateAdminMessageRequest)
    suspend fun listForRecipient(recipientGid: String): AdminMessageListResult
    suspend fun deleteExpired(): Int
}
```

Regeln:

- `send` prueft Admin-Recht serverseitig
- `listForRecipient` ist die Stelle, an der das effektive Ablaufdatum verkuerzt werden darf
- `deleteExpired` kann von Request-Pfaden und von einem periodischen Cleanup verwendet werden

## Store-Semantik

Fuer v1 soll der Store private Nachrichten getrennt vom VectorStore halten.

Empfohlene Form:

- dedizierter file-backed JSON-Store in einer einzelnen JSON-Datei
- vollständige Synchronisation im Store statt verteilter Nebenlaeufigkeit in Service/Controller

Begruendung:

- es wird nur ein kleines Nachrichtenvolumen erwartet
- Performance ist fuer v1 kein treibender Faktor
- Nachrichten werden nicht nur angehaengt, sondern ihr `expiresAt` wird beim Abruf verkuerzt und beim Cleanup werden Eintraege entfernt
- eine einzelne JSON-Datei ist dafuer einfacher und robuster als JSONL

Minimales Store-Interface:

```kotlin
interface AdminMessageStore {
    suspend fun append(record: AdminMessageRecord)
    suspend fun deleteExpired(nowEpochSeconds: Long): Int
    suspend fun loadVisibleAndShortenExpiry(
        recipientGid: String,
        nowEpochSeconds: Long,
        retrievedExpiryUpperBoundEpochSeconds: Long,
    ): List<AdminMessageRecord>
}
```

Diese Operation sollte intern:

1. alle abgelaufenen Nachrichten verwerfen
2. nur Nachrichten des Empfaengers betrachten
3. fuer sichtbare Nachrichten `expiresAt = minOf(expiresAt, retrievedExpiryUpperBound)` setzen
4. die sichtbaren Nachrichten zurueckgeben

Damit ist der kritische Lifecycle-Pfad atomar und testbar.

## Cleanup

Es soll zwei Cleanup-Wege geben:

### 1. Request-naher Cleanup

Bei `GET /v1/messages` werden vor der Rueckgabe alle abgelaufenen Nachrichten entfernt oder ignoriert. So sieht der Nutzer nie abgelaufene Daten.

### 2. Periodischer Cleanup

Zusaetzlich soll ein leichter Hintergrund-Cleanup direkt in v1 vorgesehen werden, damit alte Nachrichten auch ohne User-Traffic verschwinden.

Fuer v1 ist das Implementierungsziel:

- explizite Service-Methode `deleteExpired()`
- periodischer Aufruf ueber eine einfache serverseitige Scheduled-Komponente

Der request-nahe Cleanup bei `GET /v1/messages` bleibt zusaetzlich bestehen, damit Nutzer nie abgelaufene Nachrichten sehen.

## Client-Verhalten

Minimaler Client fuer v1:

- neuer PodAccess-Aufruf fuer `GET /v1/messages`
- neuer PodAccess-Aufruf fuer `POST /v1/admin/messages`
- einfacher Service zum Laden der Inbox
- einfache visuelle Notifikation, wenn es mindestens eine Nachricht gibt
- Abrufansicht mit Liste aus Betreff, Zeitpunkt und Text
- minimale Admin-Sende-UI als Popover am Avatar eines Nutzerbeitrags oder einer Nutzerantwort

Semantik des Admin-Popovers:

- Das Avatar bleibt fuer normale Nutzer beim bisherigen Verhalten.
- Fuer Admins bekommt das Avatar in Beitragskarten und Antwortkarten zusaetzlich die Funktion, einen kleinen Compose-Popover zu oeffnen.
- Das Popover arbeitet immer auf genau der `authorGID` des geklickten Elements.
- Das Popover soll keine freie Empfaengerauswahl enthalten; der Zielnutzer ergibt sich ausschliesslich aus dem Kontext.
- Das Popover braucht nur minimale Validierung fuer nichtleeren Betreff und nichtleeren Nachrichtentext.
- Nach erfolgreichem Senden wird eine knappe Erfolgsmeldung oder ein stilles Schliessen akzeptiert.
- Bei Fehlern soll ein kleiner lokaler Fehlerzustand im Popover angezeigt werden.

Lesestatus im Client ist rein lokal und kosmetisch:

- optional Speicherung des zuletzt lokal gesehenen `createdAt` oder einer Menge lokaler IDs
- kein Rueckschreiben an den Server

Die serverseitige TTL nach erstem Abruf bleibt davon unabhaengig.
Die serverseitige Verkuerzung des Ablaufdatums bleibt davon unabhaengig.

## Fehler- und Randfaelle

- Leere `recipientGid`, leere Subjects oder leere Bodies muessen serverseitig als Bad Request abgelehnt werden.
- Holt der User seine Inbox genau beim Ablaufzeitpunkt, gilt die Nachricht als abgelaufen, wenn `expiresAt <= now`.
- Werden mehrere Geraete parallel aktiv, muss trotzdem genau ein konsistenter Zustand fuer `expiresAt` entstehen.
- Holt ein anderer User die Inbox, duerfen fremde Nachrichten auch bei manipulierten Requests nicht sichtbar werden.
- Admin-Status wird serverseitig geprueft, nicht nur ueber Client-UI.
- Die Avatar-Aktion fuer Admin-Nachrichten darf fuer Nicht-Admins weder sichtbar noch aktiv sein.
- Selbstnachrichten von Admins an das eigene Konto sind erlaubt und brauchen weder clientseitig noch serverseitig eine Sonderbehandlung.

## Vor Implementierung festgezogene Entscheidungen

- `POST /v1/admin/messages` liefert bei Erfolg `204 No Content`
- Der Hintergrund-Cleanup ist direkt Teil von v1
- Der private Store wird als einzelne JSON-Datei umgesetzt
