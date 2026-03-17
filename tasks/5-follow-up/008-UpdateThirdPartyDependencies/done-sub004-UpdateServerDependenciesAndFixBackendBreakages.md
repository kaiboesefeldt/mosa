Ziel

Die direkt deklarierten Server-Abhaengigkeiten auf aktuelle stabile Versionen anheben und noetige Backend-Anpassungen fuer weiterhin gruene Checks umsetzen.

Scope

- Direkt deklarierte JVM-Abhaengigkeiten in `server` aktualisieren, insbesondere Ktor, Koin, Exposed, Jackson, Security-, Logging-, CLI- und Infrastruktur-Bibliotheken.
- Testabhaengigkeiten in `server` mitziehen, soweit sie fuer kompatible Builds und Tests notwendig sind.
- Unmittelbar upgradebedingte Code- und Testanpassungen im Server-Modul umsetzen.

Nicht Teil dieser Subtask

- Funktionsaenderungen ohne direkten Zusammenhang zu den Dependency-Upgrades.
- Dauerhafte Umarchitektur nur zur Umgehung einzelner Upgrade-Probleme.

Akzeptanzkriterien

- Die direkt deklarierten Server-Abhaengigkeiten sind auf die festgelegten Zielversionen aktualisiert.
- Server-Build und bestehende Server-Tests laufen wieder mit den neuen Versionen.
- Erforderliche Code-Anpassungen sind technisch auf die Upgrade-Kompatibilitaet begrenzt.
