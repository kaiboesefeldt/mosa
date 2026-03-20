# Befund

Das beobachtete Non-Heap-Load-Shedding war kein normales Schutzverhalten, sondern ein Mess- und Interpretationsfehler.

Die bisherige Non-Heap-Messung lief ueber `MemoryMXBean.nonHeapMemoryUsage` und normalisierte `used` gegen `max`, bei `max == -1` aber ersatzweise gegen `committed`.

Genau dieser Fallback ist fuer Non-Heap problematisch:

- Bei Non-Heap ist `committed` oft nur die aktuell von der JVM reservierte Groesse.
- Diese Groesse liegt haeufig sehr nah an `used`.
- Dadurch entstehen scheinbar fast volle Auslastungen wie `98.4%`, obwohl keine belastbare globale Non-Heap-Obergrenze erreicht ist.

Das erklaert das beobachtete Shedding bei unauffaelliger Gesamtlage.

# Entscheidung

Der bisherige globale Non-Heap-Wert wird nicht mehr direkt fuer Shedding verwendet.

Stattdessen wird Non-Heap jetzt pool-basiert bewertet:

- Es werden nur `MemoryPoolMXBean`s mit `type == NON_HEAP` betrachtet.
- Es werden nur Pools beruecksichtigt, die ein belastbares `usage.max > 0` liefern.
- Der kritischste dieser Pools bestimmt den Non-Heap-Auslastungswert fuer den Decider.

Damit basiert Non-Heap-Shedding nur noch auf belastbaren Obergrenzen.

Wenn kein solcher Non-Heap-Pool verfuegbar ist, liefert der Sampler keinen verwertbaren Wert und der bestehende Decider behandelt das wie "kein Shedding".

Das bleibt im bestehenden Defense-Konzept einfach:

- kein neuer Sondermechanismus ausserhalb des vorhandenen Deciders
- keine Mischung von Heap- und Non-Heap-Pools
- kein Rueckfall auf `committed` oder Gesamt-RAM

# Umsetzung

- Der Non-Heap-Decider verwendet jetzt einen eigenen pool-basierten Sampler.
- Dieser Sampler ignoriert Heap-Pools und Non-Heap-Pools ohne belastbares `max`.
- Der am staerksten ausgelastete belastbare Non-Heap-Pool steuert den Shedding-Wert.
- Der Heap-Decider bleibt separat und nutzt weiter den bestehenden Heap-Sampler.
- Der irrefuehrende Heap-Sampler-Name wurde von `NonHeapMemory` auf `HeapMemory` korrigiert.

# Tests

Neue Tests sichern ab, dass:

- Non-Heap-Pools ohne `max` nicht fuer Shedding herangezogen werden.
- Heap-Pools den Non-Heap-Decider nicht beeinflussen.
- bei mehreren belastbaren Non-Heap-Pools der kritischste Pool den Ausschlag gibt.

Der bisherige Fehlpfad ist damit direkt gegen Regression abgesichert.
