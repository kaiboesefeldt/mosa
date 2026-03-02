# Nächste Aufgabe

Die ClearTerms-Dokumente sollen abhängig davon, woher sie stammen, farblich hinterlegt werden.

Grundsätzlich gilt, dass die Baseline als "vertrauenswürdig" gilt. Die Baseline definiert bestimmte
Textblöcke mit `null`, sodass sie von einem Profil gesetzt werden müssen. Außerdem müssen alle
Platzhalter überschrieben werden.

Darüber hinaus können Profile auch andere Textblöcke sowie bereits von vorigen Profilen gesetzte
Platzhalter überschreiben.

## Ohne eingefärbten Hintergrund

Alle Texte, die unverändert aus der Baseline oder aus `null`-Überschreibungen von Textblöcken kommen,
werden ohne eingefärbten Hintergrund dargestellt. Gleiches gilt für Platzhalter-Ersetzungen von Werten,
aber nur, wenn sie in exakt einem Profil überschrieben werden (`Override-Anzahl = 1` für den Platzhalter)

Hintergrund: Das sind alle Texte, die entweder unverändert sind, oder die man über die
Profile liefern MUSS, weil ansonsten das Rendering einen Fehler liefert.

## Gelber Hintergrund

Alle Texte aus Platzhaltern, die in mehr als einem Profil der Kette überschrieben wurden.

## Roter Hintergrund

Alle Texte, die aus überschriebenen oder hinzugefügten Textblöcken kommen, werden rot hinterlegt
dargestellt. Das gilt z.B. auch für Textblöcke, die zwar in der Baseline `null` sind, dann aber
zuerst in dem einen Profil und dann noch einmal in einem nachfolgenden Profil überschrieben werden.

---

## Präzise Begriffe

- `Textblock`: Ein benannter Block im ClearTerms-Modell.
- `Null-Textblock`: Ein Textblock, der in der Baseline explizit `null` ist und daher zwingend gesetzt werden muss.
- `hinzugefügter Textblock`: Ein Textblock, den es in der Baseline nicht gibt und der über ein Profil neu hinzugefügt wurde.
- `Platzhalter`: Ein zu ersetzender Wert innerhalb eines Textes.
- `Baseline`: Die vertrauenswürdige Ausgangsdefinition.
- `Profilkette`: Die geordnete Folge angewendeter Profile (z. B. Baseline -> Default-Profil -> Operator-Profil).
- `Überschreibung`: Eine Wertsetzung in einem Profil, die den vorherigen Stand ersetzt.
- `Pflichtwert`: Ein Platzhalter, der zwingend von nachfolgenden Profilen gesetzt werden muss.
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
2. Gelb
3. Ohne eingefärbten Hintergrund

## Eindeutige Entscheidungsregeln

### 1) Rot

Ein finales Textelement wird rot, wenn mindestens eine der Bedingungen gilt:
- Es ist ein Textblock, der in der Baseline nicht mit `null` definiert ist und in mindestens einem
  Profil überschrieben wurde.
- Es ist ein Textblock mit `Override-Anzahl > 1`.
- Es ist ein neu hinzugefügter Textblock.

Beispiele:
- Baseline-Textblock `null` -> Default-Profil setzt Wert -> Operator-Profil setzt erneut Wert: rot.
- Baseline-Textblock mit Inhalt -> Operator-Profil überschreibt: rot.

### 2) Gelb

Ein Platzhalterwert wird gelb, wenn gilt: `Override-Anzahl > 1`.

Beispiele:
- Platzhalterwert wird im Default-Profil auf 7d gesetzt und im Operator-Profil auf 14d: gelb.

### 3) Ohne eingefärbten Hintergrund

Ein finales Textelement wird ohne farbigen Hintergrund angezeigt, wenn eine dieser Bedingungen gilt:
- Es stammt unverändert aus der Baseline
- Es handelt sich um einen Textblock, der in der Baseline mit `null` definiert wurde, mit
  `Override-Anzahl = 1`.
- Es handelt sich um einen Platzhalter mit `Override-Anzahl = 1`.

Beispiele:
- Baseline `null`-Block -> nur Default-Profil setzt einmal: ohne farbigen Hintergrund.
- Platzhalter -> nur Operator-Profil setzt einmal: ohne farbigen Hintergrund.

## Klärung für Fehlerfälle

- Wenn ein Null-Textblock oder Platzhalter bis zum Ende der Profilkette nicht gesetzt ist, gilt das als Renderfehler wie bisher.
- Die Farbregeln werden nur auf erfolgreich gerenderte Inhalte angewendet.
