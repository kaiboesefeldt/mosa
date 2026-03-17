Die Build-Konfiguration des Clients soll minimal auf die festgelegte Wrapper-Zielversion umgestellt werden, soweit ein Versionssprung tatsaechlich noetig ist.

Zu bearbeiten:

- Die in der Client-Build-Konfiguration direkt verwendeten Kotlin-Wrappers auf die ermittelte Zielversion anheben.
- Falls die aktuelle Konfiguration bereits auf der neuesten stabilen Version steht, keine funktional neutralen Schein-Aenderungen vornehmen.
- Nur die fuer das Wrapper-Upgrade unmittelbar noetigen buildnahen Anpassungen vornehmen.
- Sicherstellen, dass React-, Kotlin- oder Toolchain-Versionen dabei nicht stillschweigend mitgezogen werden.

Ergebnis:

- Die Build-Konfiguration referenziert die neueste stabile Wrapper-Version oder dokumentiert nachvollziehbar, dass dies bereits zuvor der Fall war.
