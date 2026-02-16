# ClearTerms Merkliste

## Profil-Override-Relaxierungen (geplant)

- `overrides.values` in `profile` soll optional werden.
- `overrides.texts.<language>.append` soll optional werden.
- Textblöcke sollen optionale Hinweise (`hints`) unterstützen, insbesondere für `null`-Blöcke, damit ableitende Profile klar wissen, welche Inhalte dort erwartet werden.
- Für Textblock-Überschreibungen soll perspektivisch ein Schweregrad definiert werden:
  - `null`-Textblöcke: „Geht nicht anders“ (Überschreibung erforderlich)
  - normal: „Kann man machen“
  - restriktiv: „Sollte nicht überschrieben werden. User: Achtung!“
  - Wenn nichts angegeben ist, gilt standardmäßig der restriktive Zustand.
  - In abgeleiteten Profilen darf der Schweregrad nur in Richtung „schwerer/strenger“ überschrieben werden, niemals in Richtung „weniger dramatisch“.

## Hinweis

Bis zur Umsetzung in Spezifikation und Implementierung kann dieses Zielmodell von der aktuellen Validierung abweichen.
