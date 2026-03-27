Diese Subtask adressiert die nach Task 035 verbliebenen langen Parameterlisten in der `IdentityConnectedView`-Struktur.

Ziel

Die UI-nahe Verdrahtung soll statt vieler Einzelparameter mit kleinen, klar benannten Props-, Context- oder Actions-Objekten arbeiten.

Durchfuehrung

1. Die laengsten Parameterlisten in `IdentityConnectedView.kt`, `IdentityConnectedViewHeaderUi.kt`, `IdentityConnectedViewBoardUi.kt` und `IdentityConnectedViewOverlayUi.kt` identifizieren.
2. Gruppen mit natuerlicher Zusammengehoerigkeit schneiden, zum Beispiel:
   - Profil- und Avatar-Daten
   - Moderation-Header-Zustand und Aktionen
   - Admin-Message-Aktionen
   - Reply-/Complaint-Aktionen
   - gemeinsame Board-Props fuer Posts und Replies
3. Fuer diese Gruppen kleine Datenklassen oder Interfaces einfuehren, die fachlich benannt sind und nur die wirklich zusammengehoerigen Felder tragen.
4. Die Aufrufer und Ziel-Funktionen auf diese Objekte umstellen.
5. Pruefen, dass die neue Struktur die Lesbarkeit verbessert und nicht nur Einzelparameter in unklare Sammelobjekte verschiebt.
6. Den Integrationscheck mit `./gradlew check` ausfuehren.

Erfolgskriterien

- Die betroffenen Funktionen haben deutlich kuerzere und besser lesbare Signaturen.
- Die neu eingefuehrten Objekte sind fachlich sinnvoll geschnitten.
- Das Verhalten bleibt unveraendert.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Die Parameterlisten bleiben faktisch gleich lang.
- Es entstehen generische God-Props ohne klare fachliche Verantwortung.
- Abhaengigkeiten werden nur versteckt statt vereinfacht.
