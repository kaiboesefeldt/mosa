Diese Subtask erweitert Task 036 um die gezielte Verbesserung der Lesbarkeit und intuitiven Verstaendlichkeit der bereits refaktorierten `IdentityConnectedView`-Struktur unter `client/ui/identityconnected`.

Ziel

Die bestehende Struktur soll nicht nur kurze Funktionssignaturen haben, sondern auch fachlich klarer lesbar werden. Breite technische Sammelobjekte sollen in inhaltlich eindeutig benannte Modelle ueberfuehrt werden. Diese Modelle duerfen bewusst etwas breiter geschnitten sein als fuer einen einzelnen Funktionsaufruf minimal noetig, wenn dadurch fachliche Wiederverwendung und Wiedererkennbarkeit steigen. Grosse UI-Dateien sollen entlang sichtbarer Verantwortlichkeiten weiter aufgeteilt werden.

Durchfuehrung

1. Die breitesten Props-, State- und Dependencies-Objekte in `ui/identityconnected` identifizieren, insbesondere dort, wo die alte Komplexitaet nur in Wrapper verschoben wurde.
2. Uebergabeobjekte fuer Content- und Reply-Karten fachlich schneiden. Bevorzugt werden klar wiederverwendbare fachliche Modelle, auch wenn sie etwas breiter sind als fuer einen einzelnen Funktionsaufruf minimal noetig, zum Beispiel fuer Renderdaten, Moderationszustand, Complaint-Flow oder Admin-Interaktionen.
3. Controller-Dependencies und Controller-State entlang fachlicher Rollen statt entlang technischer Containerbegriffe schneiden, zum Beispiel fuer Laden, Publizieren, Loeschen, Moderation, Navigation oder Composer-Flows. Wo mehrere Aufrufer dieselbe fachliche Sicht brauchen, sollen gemeinsame Modelle bevorzugt werden.
4. Sehr grosse UI-Dateien wie Content- und Moderation-UI entlang sichtbarer Teilbereiche weiter aufteilen, zum Beispiel Listen, Karten, Popover und Composer-Bausteine.
5. Rein technische Benennungen wie `*State`, `*Dependencies`, `*Mutations` oder `*Actions` dort durch fachlich staerkere Namen ersetzen, wo der Code dadurch direkt leichter lesbar wird.
6. Pruefen, dass die neue Struktur nicht wieder in neue God-Props oder neue generische Runtime-Container kippt.
7. Den Integrationscheck mit `./gradlew check` ausfuehren.

Leitplanken

- Keine Verhaltensaenderung.
- Keine Rueckkehr zu langen Roh-Parameterlisten.
- Keine generischen Sammelcontainer nur zum Weiterreichen von Daten.
- Bevorzugt werden fachlich benannte Modelle, die aus Sicht der jeweiligen UI- oder Controller-Verantwortung zusammengehoeren. Diese duerfen bewusst breiter als minimal fuer einen einzelnen Aufruf sein, wenn dadurch die Wiederverwendung und die Erkennbarkeit der fachlichen Rolle steigen.
- Die Lesbarkeit fuer einen Erstleser ist wichtiger als eine rein formale Einhaltung kurzer Signaturen.

Erfolgskriterien

- Die problematisch breiten Props- und Controller-Objekte sind in kleinere fachliche Gruppen zerlegt.
- Wiederverwendete Modelle sind fachlich stabil geschnitten und nicht nur auf den kleinsten einzelnen Aufruf optimiert.
- Die Hauptablaeufe in `ui/identityconnected` lassen sich mit weniger Dateispruengen und weniger gleichzeitig zu merkenden Feldern nachvollziehen.
- Die Benennung der zentralen Modelle erklaert ihre fachliche Rolle besser als die bisherige technische Container-Sprache.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Die Komplexitaet bleibt faktisch unveraendert und wird nur in weitere Wrapper verschoben.
- Es entstehen neue generische Zwischenobjekte ohne klare fachliche Verantwortung.
- Die Dateiaufteilung fuehrt nur zu mehr Navigationsaufwand, ohne die Teilverantwortungen klarer zu machen.
