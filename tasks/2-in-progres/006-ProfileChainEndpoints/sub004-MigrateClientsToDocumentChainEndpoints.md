Ziel

Alle betroffenen Clients und internen Aufrufer sollen den neuen Dokumentketten-Vertrag verwenden statt einzelne Dokumentstufen separat zu laden.

Scope

- Alle bestehenden Client-Aufrufe fuer einzelne Privacy-Dokumentstufen auf den neuen Privacy-Endpoint umstellen.
- Alle bestehenden Client-Aufrufe fuer einzelne Terms-Dokumentstufen auf den neuen Terms-Endpoint umstellen.
- Die Verarbeitung im Client auf den neuen Response mit `baseline` und `profiles` anpassen.
- Nicht mehr benoetigte Client-APIs fuer einzelne Dokumentstufen entfernen.

Nicht Teil dieser Subtask

- Neue UI-Funktionalitaet.
- Veraenderungen an der fachlichen Renderlogik jenseits der fuer die Migration noetigen Anpassungen.

Akzeptanzkriterien

- Der Client laedt Privacy-Dokumente nur noch ueber den neuen Dokumentketten-Endpoint.
- Der Client laedt Terms-Dokumente nur noch ueber den neuen Dokumentketten-Endpoint.
- Es bleiben keine produktiv verwendeten Client-Schnittstellen fuer einzelne Dokumentstufen zurueck.
- Die betroffenen Client-Pfade sind ueber automatisierte Tests abgesichert.
