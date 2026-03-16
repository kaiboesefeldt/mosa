Ziel

Das fachliche Modell fuer den Bezug zwischen verbundener Identitaet und aktuell angezeigter Identitaet soll im Client explizit und nachvollziehbar werden.

Scope

- Vor dem eigentlichen Refactoring die bestehenden fachlichen Zustaende und Uebergaenge dieser State-Machine ueber Tests des aktuellen Verhaltens absichern, soweit das oeffentlich beobachtbar moeglich ist.
- In `IdentitySessionStateMachine.kt` ein kleines, klar benanntes Modell fuer den Verbindungsbezug der aktuell angezeigten Identitaet einfuehren.
- Die fachlichen Zustaende `verbundene Identitaet`, `aktuell angezeigte Identitaet` und deren Beziehung voneinander trennen.
- `IdentitySessionViewState.kt` und `IdentitySession.kt` nur die dafuer noetigen, fachlich sauber benannten Daten nach aussen geben.
- Die wichtigsten Zustandsuebergaenge rund um Profilnavigation und Wechsel der verbundenen Identitaet ueber fokussierte Unit-Tests absichern.

Nicht Teil dieser Subtask

- Auslagerung der Admin-Praesentationslogik aus der View.
- Kapselung der GID-bezogenen Admin-Capability-Abfrage.
- Vollstaendige UI-Integration.

Akzeptanzkriterien

- Der fachliche Bezug zwischen verbundener und angezeigter Identitaet ist ueber einen kleinen expliziten Typ oder Zustand modelliert.
- Die State-Machine kann ausdruecken, ob die angezeigte Identitaet der verbundenen Identitaet entspricht oder nicht.
- Beim Blaettern in der Profilansicht wird kein Profil fachlich als verbunden behandelt, nur weil es angezeigt wird.
- Die neuen oder angepassten Tests pruefen mindestens `verbundenes Profil -> anderes Profil -> zurueck` und `Wechsel der verbundenen Identitaet`.
- Die Tests bilden fuer die State-Machine eine kleine explizite Uebergangsmatrix statt nur einzelner Beispielpfade ab.
- Vor der eigentlichen Strukturveraenderung existieren Tests, die das zuvor beobachtbare fachliche Verhalten dieses Ausschnitts bereits absichern.
