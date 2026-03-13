Endpoints sollen künftig nicht mehr nur Default-, Service- und Operator-Profil getrennt ausliefern, sondern die komplette Profilkette.

Das Operator-Profil soll dabei immer das letzte Profil in dieser Kette sein. Dadurch wird die Auslieferung flexibler, zum Beispiel wenn zwischen bestehende Profile weitere Profile für Versionierung oder andere Zwecke eingefügt werden sollen.

Die Aufgabe umfasst mindestens diese Punkte:

- Die bisherige Auslieferung einzelner Profiltypen über Endpoints soll durch die Auslieferung der vollständigen Profilkette ersetzt werden.
- Die Reihenfolge der zurückgegebenen Profile muss stabil und fachlich eindeutig sein.
- Das Operator-Profil muss in der gelieferten Profilkette immer das letzte Profil sein.
- Die Lösung muss so gestaltet sein, dass zusätzliche Profile in der Kette später eingeführt werden können, ohne das Endpoint-Modell erneut grundlegend anzupassen.
- Es ist zu prüfen, welche bestehenden Clients oder internen Aufrufer von der geänderten Struktur betroffen sind und entsprechend angepasst werden müssen.
- Es muss klar sein, wie sich die vollständige Profilkette in den bestehenden API-Verträgen ausdrückt.

Bei der Umsetzung ist zu klären:

- Ob bestehende Endpoints umgestellt oder durch neue Endpoints ersetzt werden sollen.
- In welchem Format die Profilkette zurückgegeben wird und wie Abwärtskompatibilität bewertet wird.
- Ob es fachliche Sonderfälle gibt, in denen heute nicht alle Zwischenprofile vorhanden sind.
- Wie Versionierungsprofile oder andere künftige Profiltypen in die Kette eingeordnet werden sollen.
