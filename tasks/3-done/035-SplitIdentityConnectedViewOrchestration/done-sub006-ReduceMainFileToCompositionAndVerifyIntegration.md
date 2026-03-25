Diese Subtask ist der verpflichtende Abschlusstest der Gesamtintegration fuer Task 035.

Ziel

Nach den vorherigen Extraktionen soll `IdentityConnectedView.kt` nur noch eine kleine Zusammensetzungsdatei sein, und die neue Struktur soll mit dem bestehenden Verhalten nachgewiesen werden.

Durchfuehrung

1. Pruefen, dass `IdentityConnectedView.kt` selbst deutlich unter dem frueheren Umfang liegt und im Zielzustand keine der beteiligten Dateien mehr als 300 Zeilen hat.
2. Verifizieren, dass die Hauptdatei nur noch Service-Zugriff, Hook-Aufrufe, abgeleitete View-Model-Daten und Rendering-Verdrahtung enthaelt.
3. Die relevanten Client-Tests fuer `IdentityConnectedView` ausfuehren.
4. Die wichtigsten UI-Pfade pruefen:
   - Topic waehlen, Posts laden und Filter setzen
   - Post waehlen, Replies laden und Reply senden
   - Composer oeffnen, schliessen und Publish-Pfad pruefen
   - Inbox oeffnen, Read-Filter umschalten und Paging pruefen
   - Complaint-Queue oeffnen, Prozessiert-Filter umschalten und Decision-Pfad pruefen
   - Admin-User-Card oeffnen, History laden und Nachricht senden
   - Mobile-Pane-Wechsel, Swipe und Back-Navigation pruefen
5. Den vollstaendigen Qualitaetscheck mit `./gradlew check` ausfuehren.

Erfolgskriterien

- Kein Teil der neuen `IdentityConnectedView`-Struktur ist groesser als 300 Zeilen.
- Die Hauptdatei ist nur noch die Kompositions- und Verdrahtungsstelle.
- Die wesentlichen funktionalen Pfade bleiben unveraendert.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Eine beteiligte Datei bleibt ueber 300 Zeilen.
- Fachliche Ablaufsteuerung verbleibt weiterhin schwerpunktmaessig im Hauptcomponent.
- Ein bisher vorhandener UI-Pfad aendert sich unbeabsichtigt.
- `./gradlew check` schlaegt fehl.
