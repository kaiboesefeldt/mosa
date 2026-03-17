Vor dem Wrapper-Upgrade soll die Testabdeckung fuer die betroffenen Client-Pfade so weit verbessert werden, dass funktionale Regressionen mit sehr geringer Wahrscheinlichkeit unbemerkt bleiben.

Zu bearbeiten:

- Die durch `kotlin-react`, `kotlin-react-dom` und `kotlin-browser` direkt betroffenen Client-Bereiche und Testluecken identifizieren.
- Fuer kritische oeffentlich beobachtbare UI- und Interaktionspfade fehlende oder zu schwache Tests ergaenzen.
- Business-nahe Client-Logik testgetrieben absichern; UI-spezifische Tests nur dort ergaenzen, wo sie reale Regressionsrisiken des Wrapper-Upgrades reduzieren.
- Test-Harnesses nur im minimal noetigen Umfang vorbereiten, damit spaetere Wrapper-Aenderungen sauber eingegrenzt werden koennen.

Ergebnis:

- Eine belastbarere Ausgangslage fuer das Wrapper-Upgrade mit deutlich reduzierter Regressionswahrscheinlichkeit.
