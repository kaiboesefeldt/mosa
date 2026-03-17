Die Auswirkungen des Kotlin-Upgrades auf Build-Warnungen sollen gezielt bewertet werden, ohne diese Aufgabe unklar mit anderen Upgrade-Themen zu vermischen.

Zu bearbeiten:

- Einen relevanten Verifikationslauf mit `--warning-mode all` ausfuehren.
- Pruefen, welche Warnungen durch das Kotlin-Upgrade neu entstehen, verschwinden oder unveraendert bleiben.
- Die bekannte `archives`-Deprecation im Client im Ergebnis klar einordnen, ohne Task `018` stillschweigend mitzuerledigen, falls sie davon unabhaengig bleibt.
- Verbleibende bewusst nicht behobene Warnungen mit kurzer Begruendung dokumentieren.
- Lucene-bezogene Warnungen duerfen fuer diese Aufgabe toleriert werden, wenn sie nicht durch das Kotlin-Upgrade verursacht werden und durch die geplante Lucene-Aktualisierung separat adressiert sind.

Ergebnis:

- Ein nachvollziehbar dokumentierter Warnungsstatus fuer den Abschluss der Aufgabe.
