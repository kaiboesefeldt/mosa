Ziel

Normale Nutzer sollen am Beitrag eine kleine Beschwerde-Aktion erhalten, die den Lesefluss moeglichst wenig stoert und vollstaendig gegen die neue Serverlogik arbeitet.

Umfang

- Erweitere den Client um einen kleinen Beschwerde-Flow pro Beitrag:
  - Aktion nur fuer fremde Beitraege sichtbar
  - Oeffnen eines kleinen Eingabezustands mit Freitext
  - explizite Nutzer-Bestaetigung vor dem Absenden
  - Statusabfrage beim Oeffnen direkt vom Server
  - kein lokales Beschwerdekennzeichen im normalen Post-Rendering
- Implementiere das Signieren der Nutzer-Beschwerde auf Basis der neuen shared Payload-Definition.
- Implementiere die HTTP-Anbindung fuer Statusabfrage und Complaint-Submit.
- Stelle Konflikte und Validierungsfehler fuer den Nutzer verstaendlich dar, ohne einen grossen neuen Dialogfluss einzufuehren.

Wichtige Randbedingungen

- UI-Code muss nur kompilieren; Business-Logik testgetrieben.
- Keine Vermischung mit der bestehenden Admin-Message-Inbox.
- Die Aktion soll auf bestehenden Post-Karten und Reply-Karten konsistent verankert werden, soweit die Hauptaufgabe das verlangt.

Erledigt wenn

- Ein normaler Nutzer kann fuer einen fremden Beitrag eine signierte Beschwerde absenden.
- Ein Nutzer wird am Absenden gehindert, wenn bereits ein offener Fall existiert.
- Die Aktion ist fuer eigene Beitraege im Client nicht verfuegbar.
