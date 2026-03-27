Diese Subtask erweitert Task 036 um eine Namensbereinigung unter `client/ui/identityconnected`: Viele interne Typen, Dateien und Hilfsmodelle tragen weiterhin den Praefix `IdentityConnectedView`, obwohl der Package-Kontext diese Zuordnung bereits leistet. Das verlaengert Signaturen und erschwert das schnelle Erfassen der fachlichen Unterschiede zwischen den Typen.

Ziel

Interne Typen und Dateien unter `ui/identityconnected` sollen kuerzere, fachlich fokussierte Namen erhalten, indem redundante `IdentityConnectedView`-Praefixe dort entfernt oder reduziert werden, wo der Package-Kontext bereits ausreicht. Dadurch sollen Typnamen schneller lesbar und Unterschiede zwischen fachlichen Rollen sichtbarer werden.

Konkrete Problemstellen

- Breite Streuung ueber nahezu das gesamte Unterpackage, insbesondere bei:
  - Controller- und Modelltypen
  - Parameter- und Kontexttypen
  - UI-Prop- und View-Modellen
  - Hilfsdateien fuer Content-, Moderation-, Admin-, Reply- und Mobile-Shell-Bereiche
- Besonders problematisch sind Namen, bei denen der informative Teil weit hinten steht, zum Beispiel:
  - `IdentityConnectedViewContentController`
  - `IdentityConnectedViewModerationControllerState`
  - `IdentityConnectedViewAdminActionDependencies`
  - `IdentityConnectedViewMobileShellActions`
  - `IdentityConnectedViewEntryContext`

Durchfuehrung

1. Alle internen Typen und Dateien unter `ui/identityconnected` darauf pruefen, ob der Praefix `IdentityConnectedView` fuer die lokale Lesbarkeit einen echten Mehrwert hat.
2. Dort, wo der Package-Kontext bereits ausreicht, den redundanten Praefix entfernen oder deutlich reduzieren.
3. Sicherstellen, dass dabei keine zu generischen Namen wie nur `State`, `Actions`, `Controller` oder `Props` entstehen. Der fachliche Teil des Namens muss klar erkennbar bleiben.
4. Den Haupteinstieg `IdentityConnectedView` und andere wenige repoweit sinnvolle Namensanker nur dann umbenennen, wenn der Nutzen klar groesser als die Umstellungskosten ist.
5. Dateinamen und Top-Level-Deklarationen konsistent gemeinsam umbenennen.
6. Alle betroffenen Aufrufer, Imports und Verweise anpassen.
7. Pruefen, dass die neuen Namen innerhalb des Packages schneller lesbar sind und nicht bloss kuerzer.
8. Den Integrationscheck mit `./gradlew check` ausfuehren.

Leitplanken

- Keine Verhaltensaenderung.
- Keine Umbenennungen ohne klaren Lesbarkeitsgewinn.
- Keine zu generischen Kurzformen, die ohne Zusatzkontext unklar werden.
- Package-Kontext soll bewusst genutzt werden, statt ihn in jedem Typnamen zu wiederholen.
- Der informative, fachliche Namensbestandteil soll moeglichst weit vorne stehen.

Erfolgskriterien

- Redundante `IdentityConnectedView`-Praefixe sind bei internen Typen und Dateien deutlich reduziert.
- Die verbleibenden Namen sind kuerzer, aber weiterhin fachlich klar und nicht generisch.
- Der fachliche Unterschied zwischen Typen ist beim Lesen von Signaturen und Aufrufen schneller erkennbar.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Es werden nur kosmetische Einzelumbenennungen vorgenommen, ohne das Paket insgesamt lesbarer zu machen.
- Die neuen Namen werden so kurz, dass sie ohne Staendigen Rueckgriff auf Dateiname oder Imports unklar sind.
- Wichtige repo-weite Namensanker werden ohne echten Nutzen mit umbenannt und erzeugen nur Churn.
