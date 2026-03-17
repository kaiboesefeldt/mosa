Produktionscode, Testcode und buildnahe Konfiguration sollen nur dort angepasst werden, wo das Kotlin-Upgrade tatsaechlich Inkompatibilitaeten oder neue Warnungen erzeugt.

Zu bearbeiten:

- Den ersten durch das Upgrade verursachten Fehler oder Regressionseffekt ueber oeffentliches Verhalten reproduzieren, soweit Business-Logik betroffen ist.
- Notwendige Aenderungen an Produktionscode und Tests minimal umsetzen.
- Kotlin-Multiplatform-, JS-, Serialization- und Wrapper-bezogene API-Anpassungen nur im erforderlichen Umfang vornehmen.
- Vorhandene Tests grün bekommen.

Ergebnis:

- Der Code laeuft mit dem neuen Kotlin-Stand ohne unbegruendete funktionale Aenderungen.
