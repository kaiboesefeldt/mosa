Diese Subtask ist der verpflichtende Abschlusstest fuer Task 018. Sie verifiziert die Gesamtintegration der Migration ueber die betroffenen Build- und Testpfade des Client-/JS-Builds.

Art des Abschlusstests:

- Vollautomatisierter Repo-Check durch die Coding-KI im lokalen Repo.

Vorbedingungen:

- Der minimale Reproduktionslauf und die technische Eingrenzung aus den vorigen Subtasks liegen vor.
- Die ausgewaehlte Migration ist im Repo umgesetzt.

Auszufuehrende Schritte:

1. Den dokumentierten minimalen Reproduktionslauf mit `--warning-mode all` ausfuehren.
2. `./gradlew clean build --warning-mode all` ausfuehren.
3. Pruefen, dass die `archives`-Deprecation in den relevanten Laeufen nicht mehr erscheint.
4. Pruefen, dass Build, Packaging und Client-Tests im betroffenen Pfad weiterhin erfolgreich laufen.
5. Verbleibende bewusst nicht behobene Warnungen im betroffenen Pfad knapp abgrenzen und begruenden.

Erfolgskriterien:

- Der minimale Verifikationslauf wurde erfolgreich ausgefuehrt.
- `./gradlew clean build --warning-mode all` wurde erfolgreich ausgefuehrt.
- Die `archives`-Deprecation erscheint in den relevanten Verifikationslaeufen nicht mehr.
- Es bleiben keine unbegruendeten neuen Warnungen oder Regressionen im betroffenen Client-/JS-Buildpfad offen.
