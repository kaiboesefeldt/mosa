Diese Subtask erweitert Task 036 um eine zweite, von den flachen Controller-Fassaden getrennte Struktur-Schwaeche: Mehrere Action-Builder unter `ui/identityconnected` arbeiten mit stark technischen `State`-, `Mutations`- oder aehnlichen Lambda-Containern. Dadurch bleibt die Logik schwer lesbar, obwohl die Funktionssignaturen bereits verkuerzt wurden.

Ziel

Die Action-Builder sollen weniger ueber technische Getter-/Setter-Buendel und staerker ueber fachlich lesbare Modelle oder klar geschnittene Kontexte arbeiten. Read-, Write- und Workflow-Verantwortungen sollen besser erkennbar werden.

Konkrete Problemstellen

- `IdentityConnectedViewContentActions.kt` zusammen mit `IdentityConnectedViewContentActionParameters.kt`:
  `IdentityConnectedViewContentActionState` und `IdentityConnectedViewContentActionMutations` bestehen weitgehend aus Getter-/Setter-Lambdas. Das versteckt die eigentliche fachliche Struktur des Content-Flows hinter technischer Indirektion.
- `IdentityConnectedViewModerationActions.kt` zusammen mit `IdentityConnectedViewModerationActionParameters.kt`:
  `IdentityConnectedViewModerationInboxState` und `IdentityConnectedViewModerationComplaintState` mischen Reads, Writes und Ablauf-Hooks in denselben Objekten. Dadurch ist Moderationslogik eher als Steuerung technischer Zugriffspunkte denn als fachlicher Ablauf lesbar.
- `IdentityConnectedViewAdminActions.kt` zusammen mit `IdentityConnectedViewAdminActionParameters.kt`:
  Auch hier werden Zustand und Mutationen ueber technische Lambda-Container weitergereicht, statt ueber klarer benannte fachliche Modelle oder Teilkontexte.

Durchfuehrung

1. Fuer die genannten Action-Builder pruefen, welche technischen Zugriffsbundles nur als indirekte Verdrahtung dienen und fachlich nicht selbsterklaerend sind.
2. Read-, Write- und Workflow-Verantwortungen sauberer schneiden, zum Beispiel in fachliche Kontexte, Editor-/Composer-Modelle, Listen-/Paging-Modelle oder Loesch-/Moderations-Flows.
3. Getter-/Setter-Lambda-Container nur dort beibehalten, wo sie einen echten Nutzen fuer Kapselung oder Hook-Verhalten haben. Reine Durchreiche-Indirektion soll entfernt werden.
4. Benennungen von Action-Eingaben so staerken, dass aus dem Typ bereits hervorgeht, welche fachliche Rolle er im Ablauf hat.
5. Pruefen, ob kleine gemeinsame Modelle fuer wiederkehrende Muster sinnvoll sind, zum Beispiel fuer Composer-Zustand, Paging-Zustand oder Entscheidungskompositionen.
6. Darauf achten, dass keine neue Ueberabstraktion entsteht. Ziel ist weniger technische Verdrahtung, nicht mehr Schichten.
7. Den Integrationscheck mit `./gradlew check` ausfuehren.

Leitplanken

- Keine Verhaltensaenderung.
- Keine Rueckkehr zu langen Roh-Parameterlisten.
- Keine neuen technischen Sammeltypen mit austauschbaren Standardnamen, wenn eine fachliche Bezeichnung moeglich ist.
- Getter-/Setter-Lambdas sind nur dann akzeptabel, wenn sie echte technische Notwendigkeit abbilden und nicht bloss Datenzugriff verschleiern.
- Die konsumierende Fachlogik soll nach dem Refactoring mit weniger mentalem Mapping zwischen Typname und tatsaechlicher Verantwortung lesbar sein.

Erfolgskriterien

- Die Action-Builder lesen sich staerker ueber fachliche Modelle und weniger ueber technische Verdrahtungscontainer.
- Read-, Write- und Workflow-Rollen sind klarer getrennt oder bewusst zusammengefasst, statt implizit vermischt zu bleiben.
- Die zentralen Action-Builder unter `ui/identityconnected` haben weniger Getter-/Setter-induzierte Indirektion.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Die bestehenden technischen State-/Mutation-Container bleiben faktisch unveraendert und werden nur umbenannt.
- Es entstehen neue, generische Abstraktionsschichten ohne bessere fachliche Lesbarkeit.
- Die Refaktorierung verschiebt Indirektion nur an andere Stellen, ohne die Action-Logik fuer Erstleser klarer zu machen.
