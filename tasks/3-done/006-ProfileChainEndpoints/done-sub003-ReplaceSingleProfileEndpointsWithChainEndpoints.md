Ziel

Die bisherigen Endpoints fuer einzelne Dokumentstufen sollen durch genau einen Endpoint pro Dokumentart ersetzt werden, der den neuen Dokumentketten-Response liefert.

Scope

- Bestehende Einzel-Endpoints fuer Privacy entfernen oder ersetzen.
- Bestehende Einzel-Endpoints fuer Terms entfernen oder ersetzen.
- Pro Dokumentart genau einen Endpoint bereitstellen.
- Die neuen Endpoints liefern den gemeinsamen Response mit `baseline` und `profiles`.
- Die Endpoints verwenden ausschliesslich die beim Pod-Start validierten Dokumentketten.

Nicht Teil dieser Subtask

- Client-Umstellung.
- Neue Dokumentarten jenseits der aktuell betroffenen.

Akzeptanzkriterien

- Es gibt genau einen oeffentlichen Endpoint fuer Privacy-Dokumentketten.
- Es gibt genau einen oeffentlichen Endpoint fuer Terms-Dokumentketten.
- Die bisherigen Endpoints fuer einzelne Dokumentstufen sind nicht mehr Teil des oeffentlichen API-Vertrags.
- Die Endpoints liefern fuer einen erfolgreich gestarteten Pod keine Teilkette und keinen Fallback.
- Das HTTP-Verhalten ist ueber automatisierte Endpoint-Tests abgesichert.
