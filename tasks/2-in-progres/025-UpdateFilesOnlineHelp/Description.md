Die Onlinehilfe des `serve`-Kommandos soll die dateibasierten Laufzeit- und Konfigurationsartefakte des MOSA-Servers vollstaendig und aktuell beschreiben.

Aktuell dokumentiert die Hilfeseite fuer Dateien nur einen Teil der im Arbeitsverzeichnis verwendeten Dateien und Verzeichnisse. Inzwischen nutzt der Server zusaetzlich weitere dateibasierte Artefakte, etwa fuer Admin-GIDs, Admin-Nachrichten, rechtliche Dokumente, Signaturschluessel und Snapshot-Schluessel. Diese fehlen in der Onlinehilfe ganz oder sind nicht ausreichend erklaert. Zusaetzlich weicht der Dateiname `imprint.yaml` vom sonst konsistenten `mosa_*`-Schema ab. Dadurch ist fuer Betreiberinnen und Betreiber nicht verlaesslich erkennbar, welche Dateien im Arbeitsverzeichnis relevant sind, welche automatisch erzeugt werden, welche manuell gepflegt werden muessen und wie sich Aenderungen zur Laufzeit auswirken.

Ziel ist eine klare, kompakte und verlaessliche Dokumentation aller serverrelevanten Dateien und Verzeichnisse, die ueber `serve --doc` angezeigt wird. Die Hilfe soll den realen aktuellen Serverzustand widerspiegeln und insbesondere Dateiname, Zweck, Erzeugungs- oder Pflegeverantwortung sowie Reload- oder Neustartverhalten verstaendlich benennen.

Die Aufgabe umfasst mindestens diese Punkte:

- Alle aktuell vom Server im Arbeitsverzeichnis verwendeten Dateien und Verzeichnisse identifizieren, die fuer Betrieb, Konfiguration oder persistente Laufzeitdaten relevant sind.
- Die bestehende Hilfeseite fuer Dateien entsprechend aktualisieren.
- Fuer jede dokumentierte Datei bzw. jedes dokumentierte Verzeichnis mindestens Zweck, Name und relevantes Laufzeitverhalten beschreiben.
- Klar zwischen manuell gepflegten Konfigurationsdateien und automatisch erzeugten bzw. persistenten Laufzeitdateien unterscheiden.
- Die Hilfe inhaltlich an die tatsaechlich implementierten Dateinamen und Verhaltensweisen anpassen, ohne veraltete oder irrefuehrende Angaben stehen zu lassen.
- Die Imprint-Konfigurationsdatei von `imprint.yaml` auf `mosa_imprint.yaml` umstellen, damit die Benennung mit den uebrigen dateibasierten Serverartefakten konsistent ist.

Akzeptanzkriterien:

- `serve --doc` beschreibt alle aktuell relevanten dateibasierten Serverartefakte im Arbeitsverzeichnis, nicht nur IP- und Identity-Blocklisten.
- Die dokumentierten Namen stimmen mit den tatsaechlich im Code verwendeten Dateinamen und Verzeichnissen ueberein.
- Fuer jede relevante Datei bzw. jedes relevante Verzeichnis ist erkennbar, ob sie oder es manuell gepflegt, automatisch erzeugt oder persistent zur Laufzeit beschrieben wird.
- Bei dateibasierten Konfigurationen mit Auto-Reload ist dieses Verhalten dokumentiert.
- Die Hilfeseite enthaelt keine fachlich ueberholten oder unvollstaendigen Angaben mehr.
- Die Imprint-Konfiguration verwendet nach der Aenderung konsistent den Dateinamen `mosa_imprint.yaml`, und die Onlinehilfe dokumentiert diesen Namen statt `imprint.yaml`.

Betroffen sind alle dateibasierten Serverartefakte, die auf Top-Level im aktuellen Arbeitsverzeichnis des MOSA-Servers erwartet, gelesen, geschrieben oder persistent angelegt werden.

Dazu gehoeren nach heutigem Stand insbesondere diese Artefakte:

- `mosa_ip_blocklist.yaml`
- `mosa_identity_blocklist.yaml`
- `mosa_admin_gids.yaml`
- `mosa_admin_messages.json`
- `mosa_privacy.yaml`
- `mosa_terms.yaml`
- `mosa_imprint.yaml`
- `mosa_pod_keypair.pem`
- `mosa_snapshot_archive_key.txt`
- `mosa_lucene_index`

Fuer die Umsetzung gelten insbesondere diese Leitplanken:

- Die Dokumentation soll alle Artefakte abdecken, die auf Top-Level im CWD des Servers liegen oder dort erwartet werden, nicht nur die bisher bereits erwaehnten Beispiele.
- Sensible oder sicherheitsrelevante Dateien sollen nicht nur genannt, sondern auch hinsichtlich Zweck, Sensibilitaet und sorgfaeltigem Betreiberumgang knapp und klar beschrieben werden.
- Primaerer Anpassungsort ist `server/src/main/resources/doc/serve/files.md`. Andere `serve`-Dokumentation soll nur dann mit angepasst werden, wenn dies fuer Konsistenz oder Verstaendlichkeit unmittelbar noetig ist.
- Die Umbenennung von `imprint.yaml` auf `mosa_imprint.yaml` ist als harte Umstellung ohne parallele Unterstuetzung des alten Dateinamens zu behandeln.
