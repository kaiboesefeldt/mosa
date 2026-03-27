Diese Subtask erweitert Task 036 um die Beseitigung einer nach dem Parameterlisten-Refactoring verbliebenen Struktur-Schwaeche: Mehrere Controller- und Shell-Modelle werden aktuell als grosse flache Fassaden gebaut, obwohl ihre Bestandteile bereits in fachlich getrennten Gruppen wie State, Actions, Lookups, Refs oder Callbacks vorliegen.

Ziel

Die betreffenden Modelle sollen dort, wo bereits klar getrennte Teilmodelle existieren, nicht mehr durch Builder-Funktionen in grosse flache Datenklassen kopiert werden. Stattdessen sollen sie diese Teilmodelle direkt aggregieren. Dadurch sollen die vorhandenen fachlichen Gruppierungen sichtbar bleiben und die doppelte Strukturdefinition entfallen.

Konkrete Problemstellen

- `IdentityConnectedViewContentControllerModel.kt`:
  `IdentityConnectedViewContentController` kopiert `IdentityConnectedViewContentControllerState`,
  `IdentityConnectedViewContentControllerActions` und `IdentityConnectedViewContentControllerLookups`
  flach in ein einziges grosses Modell.
- `IdentityConnectedViewModerationControllerModel.kt`:
  `IdentityConnectedViewModerationController` kopiert `IdentityConnectedViewModerationControllerState`,
  `IdentityConnectedViewModerationControllerRefs` und `IdentityConnectedViewModerationControllerCallbacks`
  inklusive abgeleiteter Sichtfelder in eine grosse flache Fassade.
- `IdentityConnectedViewAdminControllerModel.kt` und `IdentityConnectedViewAdminController.kt`:
  Der Admin-Controller expose't Zustand und Aktionen bereits als gemeinsames Modell, aber ebenfalls flach statt als klar aggregierte Teilrollen.
- `IdentityConnectedViewReplyController.kt`:
  Auch hier werden Zustand und Aktionen in einem flachen Rueckgabemodell vermischt, statt als lesbare fachliche Gruppen sichtbar zu bleiben.
- `IdentityConnectedViewMobileShell.kt`:
  Die Funktion arbeitet bereits mit `IdentityConnectedViewMobileShellState`, `...Refs` und `...Actions`, liefert aber wieder ein flaches `IdentityConnectedViewMobileShell`-Modell zurueck.
- `IdentityConnectedViewViewportController.kt`:
  Kleinerer, aber aehnlicher Randfall. Das Rueckgabemodell mischt Zustand und Mutation (`setMobilePane`) flach, obwohl sich hier ein klarer Schnitt in Viewport-Zustand und Navigation anbietet.
- `IdentityConnectedViewProfileController.kt`:
  Ebenfalls ein kleinerer Randfall. Das Rueckgabemodell mischt Sichtzustand, Animationen, Toast-Zustand und Aktionen in einer flachen Struktur. Auch wenn hier noch keine Teilmodelle existieren, soll geprueft werden, ob eine Aggregation oder fachlich klarere Gruppierung die Lesbarkeit verbessert.

Durchfuehrung

1. Fuer die genannten Stellen pruefen, welche bestehenden Teilmodelle bereits fachlich brauchbar geschnitten sind und direkt aggregiert werden koennen.
   Bei kleineren Randfaellen ohne bestehende Teilmodelle ist zu pruefen, ob neue fachliche Teilmodelle den Code real lesbarer machen oder ob die flache Form dort noch vertretbar ist.
2. Flache Rueckgabemodelle, die nur vorhandene Teilmodelle erneut ausbreiten, auf Aggregation umstellen.
3. Builder-Funktionen entfernen oder deutlich vereinfachen, wenn sie nur noch triviale Ableitungen oder Sichtfilter kapseln.
4. Abgeleitete Felder, die fuer die UI wirklich hilfreich sind, entweder in klar benannte View-Teilmodelle verschieben oder als berechnete Properties an der passenden Aggregation belassen.
5. Die UI-Aufrufer so anpassen, dass fachliche Rollen wieder direkt sichtbar werden, zum Beispiel ueber `state`, `actions`, `lookups`, `refs` oder klarere fachliche Teilobjekte statt ueber einen grossen Feldsack.
6. Pruefen, dass dabei keine neue uebertechnische Verschachtelung entsteht. Ziel ist bessere Lesbarkeit, nicht mehr Indirektion um ihrer selbst willen.
7. Den Integrationscheck mit `./gradlew check` ausfuehren.

Leitplanken

- Keine Verhaltensaenderung.
- Keine Rueckkehr zu langen Roh-Parameterlisten.
- Keine neuen Builder-Layer, die wiederum nur Daten umkopieren.
- Aggregation ist zu bevorzugen, wenn die Teilmodelle bereits eine klare fachliche Rolle haben.
- Abgeleitete Sichtdaten duerfen bleiben, aber nur dort, wo sie eine reale Lesbarkeitshilfe fuer die konsumierende UI darstellen.

Erfolgskriterien

- Die identifizierten flachen Controller-/Shell-Fassaden sind durch Aggregation oder klarer geschnittene View-Teilmodelle ersetzt.
- Doppelte Strukturdefinitionen zwischen `State`/`Actions`/`Lookups`/`Refs` und flachen Rueckgabemodellen sind reduziert oder entfernt.
- UI-Code zeigt die fachlichen Rollen der konsumierten Modelle wieder sichtbar an.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Die grossen flachen Fassaden bleiben bestehen und werden nur umbenannt.
- Neue Aggregationen fuehren zu unklaren, generischen Wrappern ohne echte Strukturverbesserung.
- Abgeleitete UI-Hilfsfelder werden unkontrolliert in weitere Zwischenmodelle verschoben, sodass die Navigation eher schlechter als besser wird.
