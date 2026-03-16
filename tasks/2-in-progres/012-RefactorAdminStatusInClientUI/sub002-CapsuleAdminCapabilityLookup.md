Ziel

Die GID-bezogene Admin-Capability-Abfrage soll im Client in einer kleinen, selbsterklaerenden API gekapselt werden.

Scope

- Vor dem Verschieben oder Umformen der Capability-Logik das derzeitige fachliche Verhalten der Admin-Abfrage und der Veraltungsbehandlung ueber Tests oder Teilintegrationstests absichern.
- `ContentCapabilitiesService.kt` so erweitern oder umformen, dass dort eine klar benannte API fuer den Admin-Status einer GID entsteht.
- Die GID-bezogene Abfrage ueber `PodAccess.kt` weiterverwenden, aber die View von verstreuter Fetch- und Versionslogik entlasten.
- Race-Condition-Sicherungen gegen veraltete Capability-Responses in der Kapselung lokal und verstaendlich organisieren.
- Das oeffentlich beobachtbare Verhalten der neuen Kapselung mit fokussierten Tests absichern.

Nicht Teil dieser Subtask

- Umgestaltung der sichtbaren UI.
- Vollstaendige Einbindung in Header-Badge und Profil-Badge.
- Allgemeine Umstrukturierung der gesamten Connected-View ausserhalb des Capability-Pfads.

Akzeptanzkriterien

- `ContentCapabilitiesService.kt` bietet eine kleine API mit fachlich erkennbarem Bezug auf Admin-Capabilities.
- Die View muss keine verteilte Versions- oder Veraltungslogik fuer Capability-Responses mehr selbst verwalten.
- Veraltete Responses koennen keinen neueren Admin-Status mehr sichtbar ueberschreiben.
- Die Tests decken mindestens einen Wechsel zwischen Admin- und Nicht-Admin-Antworten sowie einen veralteten Response ab.
- Die Tests pruefen explizit unterschiedliche Ankunftsreihenfolgen konkurrierender Responses fuer dieselbe fachliche Anzeige-Situation.
- Vor dem eigentlichen Refactoring ist das bestehende fachliche Verhalten der Abfragekette durch Tests abgesichert, notfalls ueber eine kleine Teilintegration statt nur ueber reine Unit-Tests.
