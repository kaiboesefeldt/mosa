# Review

Stand: 2026-03-30

Die Umsetzung trifft die beabsichtigte Fachsemantik im Happy Path weitgehend. Die neue Self-Delete-API ist vorhanden, der Client entfernt lokal erst nach erfolgreicher Pod-Loeschung, und die Kernfaelle fuer Posts, Complaints und Admin-Nachrichten sind automatisiert abgedeckt.

Nachtrag 2026-03-30:

Die unten dokumentierten Findings wurden nachbearbeitet.

- Finding 1 ist behoben: `IdentityDeletionService` erstellt vor der Kaskade einen Snapshot der betroffenen Inhalte und stellt diese bei Fehlern in der Moderationsbereinigung wieder her. Der Fehlerpfad ist ueber einen echten Endpoint-Test abgesichert.
- Finding 2 ist behoben: Die Admin-Message-Loeschung ist jetzt sowohl fuer `recipientGid` als auch fuer `senderGid` explizit getestet, auf Store- und Integrationsniveau.

Verifikation der Nacharbeit:

- `./gradlew :server:test --tests straightway.mosa.content.complaint.PostComplaintIntegrationTest --tests straightway.mosa.message.SqliteAdminMessageStoreTest`
- `./gradlew check`

Die urspruenglichen Findings bleiben zu Dokumentationszwecken unten stehen, sind aber als erledigt zu verstehen.

## Finding 1: Kaskadenloeschung ist nicht atomar und kann fachlich inkonsistente Teilloeschungen erzeugen

Schweregrad: hoch

Betroffene Stellen:

- `server/src/main/kotlin/straightway/mosa/content/IdentityDeletionService.kt:11`
- `server/src/main/kotlin/straightway/mosa/content/IdentityDeletionService.kt:12`
- `server/src/main/kotlin/straightway/mosa/content/IdentityDeletionService.kt:13`
- `server/src/main/kotlin/straightway/mosa/content/IdentityDeletionService.kt:14`

Problem:

Die Loeschung laeuft als drei voneinander unabhaengige Schritte: erst Content im Vector Store, dann Complaints in SQLite, dann Admin-Nachrichten in SQLite. Wenn einer der spaeteren Schritte fehlschlaegt, sind die frueheren Schritte bereits dauerhaft ausgefuehrt. Der Endpoint antwortet dann mit Fehler, aber die Identitaet ist pod-seitig nur teilweise geloescht.

Das ist fachlich kritisch, weil der Client in diesem Fall die lokale Identitaet bewusst gerade nicht entfernt. Fuer den Nutzer entsteht damit genau der inkonsistente Zustand, den die Task vermeiden wollte: lokal noch vorhanden, auf dem Pod aber eventuell bereits ohne Inhalte oder ohne Teilmengen der Moderationsdaten.

Abdeckung:

Die vorhandenen Tests decken nur den Erfolgsfall und den UI-Fall `Pod-Loeschung liefert false` ab, nicht aber einen echten serverseitigen Teilfehler innerhalb der Kaskade.

- `server/src/test/kotlin/straightway/mosa/content/complaint/PostComplaintIntegrationTest.kt:530`
- `client/src/jsTest/kotlin/straightway/mosa/client/ui/IdentitySessionStateMachineTest.kt:216`

Empfehlung:

Die Kaskade braucht ein klares Konsistenzmodell. Entweder alle betroffenen Daten in eine atomare serverseitige Loeschoperation bringen, oder einen expliziten fachlichen Kompensations-/Recovery-Mechanismus definieren und testen. Ohne das ist der Fehlerpfad fachlich unvollstaendig.

Status:

Erledigt durch Snapshot- und Restore-Logik fuer die geloeschten Inhalte sowie einen Integrationstest, der den Fehlerpfad reproduziert und die Wiederherstellung verifiziert.

## Finding 2: Die neue Admin-Message-Loeschsemantik ist nicht vollstaendig getestet

Schweregrad: mittel

Betroffene Stellen:

- `server/src/main/kotlin/straightway/mosa/message/SqliteAdminMessageStore.kt:40`
- `server/src/main/kotlin/straightway/mosa/message/SqliteAdminMessageStore.kt:43`
- `server/src/main/kotlin/straightway/mosa/message/SqliteAdminMessageStore.kt:44`
- `server/src/test/kotlin/straightway/mosa/content/complaint/PostComplaintIntegrationTest.kt:568`
- `server/src/test/kotlin/straightway/mosa/content/complaint/PostComplaintIntegrationTest.kt:650`
- `server/src/test/kotlin/straightway/mosa/message/SqliteAdminMessageStoreTest.kt:9`

Problem:

Die Implementierung loescht Admin-Nachrichten sowohl bei `recipientGid == gid` als auch bei `senderGid == gid`. Das ist fachlich plausibel, aber nur die Empfaenger-Seite ist aktuell direkt verifiziert. Im Integrationstest wird eine Nachricht an die geloeschte Identitaet erzeugt und deren Inbox danach geprueft. Der zweite Zweig `senderGid == gid` ist weder im Store-Test noch im Integrationspfad abgesichert.

Damit ist die Testabdeckung fuer die neue Loeschlogik nicht vollstaendig. Gerade weil die Semantik in dieser Task erweitert wurde, sollte jede geloeschte Verknuepfungsrichtung explizit belegt sein.

Empfehlung:

Einen fokussierten Store-Test fuer `deleteForIdentity` auf Admin-Nachrichten mit beiden Richtungen ergaenzen und einen Integrationsfall aufnehmen, in dem die geloeschte Identitaet selbst Sender einer Admin-Nachricht ist.

Status:

Erledigt durch einen dedizierten Store-Test fuer beide Richtungen und einen Integrationsfall fuer geloeschte Sender.
