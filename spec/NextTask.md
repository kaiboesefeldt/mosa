# Nächste Aufgabe

Die ClearTerms-Dokumente sollen abhängig davon, woher sie stammen, farblich hinterlegt werden.

Grundsätzlich gilt, dass die Baseline als "vertrauenswürdig" gilt. Die Baseline definiert bestimmte
Textblöcke mit `null`, sodass sie von einem Profil überschrieben werden müssen. Außerdem definiert
sie Platzhalter ohne Defaultwert, die ebenfalls überschrieben werden müssen.

Darüber hinaus können Profile auch andere Textblöcke und Platzhalter überschreiben, oder sie können
mehrfach überschrieben werden von aufeinander aufbauenden Profilen.

## Hellgrüner Hintergrund

Alle Texte, die aus `null`-Überschreibungen kommen oder die Pflichtersetzungen von Werten sind,
werden hellgrün hinterlegt dargestellt. Das gilt aber nur, wenn sie in exakt einem Profil
überschrieben werden.

Hintergrund: Das sind praktisch alle Texte, die man über die Profile liefern MUSS, weil ansonsten
das Rendering einen Fehler liefert.

## Gelber Hintergrund

Alle anderen Texte, die aus Platzhaltern kommen und nur in einem einzigen Profil überschrieben sind,
werden gelb hinterlegt dargestellt.

## Roter Hintergrund

Alle Texte, die aus überschriebenen Textblöcken kommen, oder die Platzhalter mehr als einmal
überschreiben. werden rot hinterlegt dargestellt. Das gilt z.B. auch für Textblöcke, die zwar in
der Baseline `null` sind, dann aber zuerst im Default-Profil und dann noch einmal im Operator-Profil
überschrieben werden.

# Kein Hintergrund

Alle anderen Texte werden ohne spezifischen Hintergrund gerendert.

---

## Präzise Begriffe

- `Textblock`: Ein benannter Block im ClearTerms-Modell.
- `Platzhalter`: Ein zu ersetzender Wert innerhalb eines Textes.
- `Baseline`: Die vertrauenswürdige Ausgangsdefinition.
- `Profilkette`: Die geordnete Folge angewendeter Profile (z. B. Baseline -> Default-Profil -> Operator-Profil).
- `Überschreibung`: Eine Wertsetzung in einem Profil, die den vorherigen Stand ersetzt.
- `Pflichtwert`: Ein Platzhalter ohne Defaultwert in der Baseline, der zwingend von Profilen gesetzt werden muss.
- `Null-Textblock`: Ein Textblock, der in der Baseline explizit `null` ist und daher zwingend gesetzt werden muss.
- `Override-Anzahl`: Anzahl der Profile in der Profilkette, die für ein konkretes finales Textelement (Block oder Platzhalterwert) eine Überschreibung liefern.

Hinweis zur Zählung:
- Es zählt auf Profilebene, nicht auf Dateiebene.
- Ein Profil zählt pro konkretem Zielwert höchstens einmal, auch wenn intern mehrfach zusammengeführt wird.

## Ziel der Farbgebung

Die Farbe wird auf den final gerenderten Text angewendet:
- Bei Textblöcken: auf den gesamten finalen Block.
- Bei Platzhaltern: nur auf den eingesetzten Text des Platzhalters.

## Priorität der Farbregeln

Wenn mehrere Regeln gleichzeitig zutreffen, gilt diese Reihenfolge:
1. Rot
2. Hellgrün
3. Gelb
4. Kein Hintergrund

## Eindeutige Entscheidungsregeln

### 1) Rot

Ein finales Textelement wird rot, wenn mindestens eine der Bedingungen gilt:
- Es ist ein Textblock, der in mindestens einem Profil überschrieben wurde.
- Es ist ein Platzhalterwert mit `Override-Anzahl > 1`.

Beispiele:
- Baseline `null` -> Default-Profil setzt Wert -> Operator-Profil setzt erneut Wert: rot.
- Baseline-Textblock mit Inhalt -> Operator-Profil überschreibt: rot.

### 2) Hellgrün

Ein finales Textelement wird hellgrün, wenn beide Bedingungen gelten:
- Es stammt aus einer Pflichtsetzung, also:
  - Textblock war in Baseline `null`, oder
  - Platzhalter war in Baseline ohne Defaultwert.
- `Override-Anzahl == 1`.

Beispiele:
- Baseline `null`-Block -> nur Default-Profil setzt einmal: hellgrün.
- Platzhalter ohne Default -> nur Operator-Profil setzt einmal: hellgrün.

### 3) Gelb

Ein finales Textelement wird gelb, wenn beide Bedingungen gelten:
- Es ist ein Platzhalterwert, der nicht unter die Hellgrün-Regel fällt.
- `Override-Anzahl == 1`.

Beispiele:
- Platzhalter mit Baseline-Default -> genau ein Profil setzt abweichenden Wert: gelb.

### 4) Kein Hintergrund

Kein Hintergrund für alle übrigen Fälle, insbesondere:
- Werte, die unverändert aus der Baseline kommen.
- Platzhalter mit Baseline-Default, die nie überschrieben wurden.
- Textblöcke, die nie in Profilen überschrieben wurden.

## Klärung für Fehlerfälle

- Wenn ein Pflichtwert (Null-Textblock oder Platzhalter ohne Defaultwert) bis zum Ende der Profilkette nicht gesetzt ist, gilt das als Renderfehler wie bisher.
- Die Farbregeln werden nur auf erfolgreich gerenderte Inhalte angewendet.
