Der Clean-Build erzeugt unter Gradle `9.4.0` weiterhin eine Gradle-Deprecation im Client-Projekt:

- `The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10.`

Die Warnung wird derzeit beim Konfigurieren des Kotlin-Multiplatform-JS-Builds im Client sichtbar und ist kein einfacher lokaler API-Aufruf, der mit einem kleinen Buildscript-Patch verschwindet.

Ziel ist, die verbleibende Gradle-Deprecation so zu beseitigen, dass der Client-Build auch unter Gradle `10` kompatibel bleibt, ohne das Build-Verhalten unbeabsichtigt zu aendern.

Die Aufgabe ist bewusst eng auf diese einzelne Deprecation im Kotlin-Multiplatform-JS-Build geschnitten. Ein allgemeines Kotlin- oder Plugin-Upgrade ist nicht automatisch Teil der Aufgabe. Wenn sich zeigt, dass die Warnung nur durch ein Upgrade des Kotlin-Gradle-Plugins oder eines eng gekoppelten Plugins sinnvoll beseitigt werden kann, ist dieses Upgrade nur dann innerhalb der Aufgabe zulaessig, wenn es die minimal notwendige Aenderung darstellt und keine breitere Upgrade-Welle ausloest. Andernfalls ist der Befund als Blocker oder Folgeaufgabe zu dokumentieren.

Die Aufgabe umfasst mindestens diese Punkte:

- Den kleinsten reproduzierbaren Gradle-Lauf bestimmen, in dem die Deprecation verlaesslich sichtbar wird, und die Ursache bis zu der beteiligten Konfiguration, dem beteiligten Plugin oder der beteiligten Plugin-Interaktion eingrenzen.
- Die genaue Ursache der `archives`-Nutzung im Zusammenspiel von Kotlin Multiplatform, JS-Target und aktuellen Plugins eingrenzen.
- Pruefen, ob die Warnung durch eine Buildscript-Anpassung, ein geaendertes JS-Target-Setup oder ein Plugin-Upgrade verschwindet.
- Die minimal notwendige Migration im Client-Build umsetzen.
- Sicherstellen, dass sich Packaging, Artefakt-Erzeugung, Browser-Build und Testausfuehrung des Clients durch die Aenderung nicht unbeabsichtigt veraendern.
- Den relevanten Verifikationslauf mit `--warning-mode all` ausfuehren und bestaetigen, dass genau diese Deprecation nicht mehr erscheint.

Besonders zu klaeren:

- Ob die Warnung direkt aus dem verwendeten Kotlin-Plugin stammt oder aus einer veralteten JS-/Browser-Konfiguration im Projekt.
- Ob ein Kotlin-Plugin-Upgrade notwendig ist und mit den Repo-Vorgaben zu Kotlin `2.2.x` vereinbar waere.
- Ob fuer die Beseitigung der Warnung Aenderungen an Artefakt-Erzeugung, Distribution oder Packaging im Client noetig sind.
- Ob die Warnung bereits bei der reinen Projektkonfiguration oder erst durch konkrete Build-Tasks sichtbar wird und welcher minimale Reproduktionsbefehl fuer kuenftige Regressionstests taugt.

Nicht Teil der Aufgabe:

- Ein allgemeines Upgrade der Kotlin-Version, sofern es nicht die kleinste tragfaehige Loesung fuer genau diese Deprecation ist.
- Unabhaengige Build-Warnungen oder Deprecations ausserhalb des hier betroffenen Client-/JS-Buildpfads.
- Funktionale Aenderungen am Client, die nicht direkt fuer die Beseitigung dieser Deprecation noetig sind.

Abnahme:

- Ein dokumentierter minimaler Reproduktionslauf vor der Aenderung und ein entsprechender Verifikationslauf nach der Aenderung sind bekannt.
- Die `archives`-Deprecation erscheint im relevanten Verifikationslauf mit `--warning-mode all` nicht mehr.
- Der Client-Build bleibt funktional intakt; insbesondere Build, Packaging und Client-Tests laufen weiterhin wie erwartet.
- Falls am Ende weitere bewusst nicht behobene Warnungen im betroffenen Pfad verbleiben, sind sie kurz abgegrenzt und begruendet.
