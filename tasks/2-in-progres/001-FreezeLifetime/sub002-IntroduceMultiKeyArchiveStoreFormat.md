Diese Subtask fuehrt ein neues Dateiformat fuer Freeze-Archivschluessel ein, das mehrere Schluessel dauerhaft vorhalten kann.

Ziel ist, dass der Snapshot-Restore kuenftig nicht nur den aktuell aktiven Archivschluessel, sondern auch historische Schluessel per `keyId` aufloesen kann.

Umfang:

- Der file-basierte Archivschluessel-Store verwendet ein neues Dateiformat mit mehreren Schluesseln.
- Der aktive Schluessel ist im neuen Format eindeutig erkennbar.
- Die aktive Schluesselauswahl bleibt von der historischen Aufloesung getrennt.
- Der Store kann historische Schluessel dauerhaft per `keyId` aufloesen.
- Die vorhandenen Tests fuer den file-basierten Store werden angepasst oder erweitert.

Wichtige Randbedingungen:

- Eine Migration des bisherigen Ein-Schluessel-Dateiformats wird nicht implementiert.
- Eine Rueckwaertskompatibilitaet fuer das alte Archivschluessel-Dateiformat wird nicht eingebaut.
- Die konkrete Form des neuen Dateiformats darf pragmatisch in der Implementierung festgelegt werden, solange aktiver Schluessel und Historie eindeutig modelliert sind.

Erfolgskriterien:

- Der Store kann aus dem neuen Format einen aktiven Schluessel und mindestens einen historischen Schluessel laden.
- `activeKey()` und `findByKeyId(...)` sind fachlich getrennt und eindeutig getestet.
- Ein altes Dateiformat wird nicht stillschweigend als gueltig akzeptiert.
