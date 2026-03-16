Ziel

Die direkt deklarierten Client-Abhaengigkeiten inklusive relevanter NPM-Pakete auf aktuelle stabile Versionen anheben und daraus entstehende Frontend-Breaks beheben.

Scope

- Kotlin-, Ktor-, Wrapper- und weitere direkt deklarierte JS-Abhaengigkeiten in `client` aktualisieren.
- Direkt deklarierte NPM-Pakete in `client` auf sinnvolle stabile Versionen anheben.
- Notwendige kleine Anpassungen an Client-Code, Testcode oder Build-Konfiguration vornehmen, wenn Upgrades APIs oder Build-Verhalten aendern.

Nicht Teil dieser Subtask

- Server-spezifische Bibliotheksupdates.
- Grundsaetzliche UI-Redesigns oder fachliche Verhaltensaenderungen ohne unmittelbaren Upgrade-Bezug.

Akzeptanzkriterien

- Die direkt deklarierten Client-Abhaengigkeiten sind auf die festgelegten Zielversionen aktualisiert.
- Client-Build und bestehende Client-Tests laufen wieder mit den neuen Versionen.
- Upgradebedingte Anpassungen bleiben auf beobachtbares Kompatibilitaetsverhalten beschraenkt.
