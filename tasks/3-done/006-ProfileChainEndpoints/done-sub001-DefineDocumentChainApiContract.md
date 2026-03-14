Ziel

Der neue API-Vertrag fuer Dokumentketten soll als gemeinsame Grundlage fuer Server und Client sauber eingefuehrt werden.

Scope

- Ein gemeinsames DTO fuer den neuen Response anlegen.
- Der Response trennt `baseline` und `profiles` in zwei eigene Felder.
- `baseline` enthaelt genau das kanonische Baseline-JSON.
- `profiles` enthaelt die geordnete Liste kanonischer Profil-JSONs von der ersten Profilstufe bis zum Operator-Profil.
- Der Vertrag gilt fuer alle betroffenen Dokumentarten.

Nicht Teil dieser Subtask

- Aufloesung der Parent-Kette aus Dateien oder Konfiguration.
- Pod-Startvalidierung.
- Umstellung bestehender Endpoints oder Clients.

Akzeptanzkriterien

- Es gibt ein gemeinsames DTO fuer die Auslieferung einer Dokumentkette.
- Das DTO drueckt die Trennung zwischen Baseline und Profilkette explizit aus.
- Die Reihenfolge von `profiles` ist im Vertrag fachlich eindeutig dokumentiert.
- Der neue Vertrag ist ueber automatisierte Tests auf Serialisierung oder oeffentlich beobachtbares Verhalten abgesichert.
