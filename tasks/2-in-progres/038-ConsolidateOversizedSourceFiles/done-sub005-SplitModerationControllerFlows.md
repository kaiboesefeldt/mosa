Ziel dieser Subtask ist, `client/src/jsMain/kotlin/straightway/mosa/client/ui/identityconnected/moderation/ModerationController.kt` in kleinere, fachlich klarere Interaktionsablaeufe zu zerlegen.

Ausgangsproblem:

- Die Datei haelt gleichzeitig Inbox-Moderation und Complaint-Queue mitsamt Composer-, Decision-, Pagination- und Popover-Zustand.
- Zusaetzlich mischt der Hook State-Persistenz, asynchrone Ladeablaeufe, DOM-Outside-Click-Behandlung und Action-Verdrahtung.

Erwartetes Ergebnis:

- Inbox- und Complaint-Queue-Ablauf sind als getrennte, klar benannte Teilcontroller oder Hooks organisiert.
- DOM-nahe Popover- und Outside-Click-Logik ist von den fachlichen Moderationsablaeufen entkoppelt.
- Das sichtbare Verhalten der Moderationsoberflaeche bleibt unveraendert.
