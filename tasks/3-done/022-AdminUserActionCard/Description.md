Die Admin-Aktion zum Senden einer Nachricht an einen Nutzer soll im Client nicht mehr an einen konkreten Post oder Reply gebunden wirken.

Aktuell entsteht der Eindruck, dass eine Admin-Nachricht inhaltlich auf genau den Beitrag oder die Antwort bezogen sein muss, von der aus sie gestartet wurde. Das ist fachlich irrefuehrend, weil Admin-Nachrichten ein userbezogener Kanal sind und nicht Teil eines normalen Reply- oder Direktnachrichten-Systems.

Ziel ist ein klarer userbezogener Einstieg fuer Admin-Aktionen direkt an der dargestellten Person. Im ersten Wurf soll dazu ein Klick auf Avatar oder Fictional Name eines fremden Users eine kompakte Aktionskarte oeffnen. Diese Karte zeigt den Zieluser sichtbar an und bietet darunter Admin-Aktionen an, mindestens das Senden einer Admin-Nachricht. Die Karte soll dabei nicht winzig oder nur tooltip-artig sein, sondern komfortabel gross genug fuer Avatar, Name und Aktionen, so dass sie auf Desktop und Mobilgeraeten ruhig und gut bedienbar wirkt.

Die Aufgabe umfasst mindestens diese Punkte:

- Fuer fremde User gibt es im Client einen klickbaren userbezogenen Einstieg ueber Avatar und/oder Fictional Name.
- Der Einstieg oeffnet eine kompakte Aktionskarte oder ein Popup, das klar auf den Nutzer bezogen ist und nicht wie ein normales Social-Profil wirkt.
- Die Aktionskarte soll komfortabel gross genug sein, um Nutzerdarstellung und Aktionen ohne gequetschten Eindruck aufzunehmen.
- In der Karte werden Avatar und Fictional Name des Zielusers sichtbar angezeigt.
- Die Karte enthaelt im ersten Wurf mindestens die Aktion `Admin-Nachricht senden`.
- Das Wording der Aktion und des Dialogs muss klar machen, dass es sich um eine Admin-Nachricht handelt und nicht um normale Direktkommunikation.
- Die bestehende Funktion zum Erstellen und Senden von Admin-Nachrichten soll weiterverwendet werden, statt einen zweiten technischen Pfad einzufuehren.
- Der bisherige content-bezogene Einstieg an Post oder Reply soll entfernt oder so ersetzt werden, dass nicht mehr der Eindruck einer inhaltsbezogenen Nachricht entsteht.
- Die Bedienung muss auf Desktop und Mobilgeraeten sinnvoll funktionieren.

Bei der Umsetzung ist zu klaeren:

- Ob Avatar und Fictional Name beide klickbar sind oder nur ein klar abgegrenzter Teil des User-Blocks.
- Wie die Aktionskarte fuer normale Nutzerkonten, eigene Inhalte und Admin-Konten sichtbar oder unsichtbar sein soll.
- Wie die Karte geschlossen wird, zum Beispiel per erneuten Klick, Klick ausserhalb oder explizitem Close.
- Ob die Aktion direkt den bestehenden Admin-Nachrichtendialog oeffnet oder zuerst eine kleine Zwischenansicht innerhalb der Karte zeigt.
- Wie verhindert wird, dass die User-Karte mit einem oeffentlichen Profil oder mit normaler Interaktion verwechselt wird.
- Ob fuer den ersten Wurf nur eine einzige Aktion sichtbar ist oder ob die Karte gestalterisch schon Platz fuer spaetere Admin-Aktionen lassen soll.
- Welche Mindestbreite und welcher mobile Darstellungsmodus noetig sind, damit die Karte nicht wie ein kleines Hover-Tooltip wirkt.

Akzeptanzkriterien:

- Ein Admin kann bei einem dargestellten fremden User ueber dessen User-Block eine Aktionskarte oeffnen.
- Die Aktionskarte zeigt Avatar und Fictional Name des Zielusers.
- Die Aktionskarte bietet die Aktion `Admin-Nachricht senden` an.
- Das Ausloesen dieser Aktion fuehrt in denselben Admin-Nachrichtenfluss wie bisher.
- Die UI vermittelt nicht, dass die Nachricht eine Antwort auf den aktuell sichtbaren Post oder Reply sein muss.
- Die bisherige content-bezogene Platzierung der Aktion ist im UI nicht mehr die primaere oder einzige Bedienmoeglichkeit.
- Fuer Nicht-Admins erscheint keine zugaengliche Admin-Aktionskarte.
- Die Aktionskarte ist sichtbar komfortabel gross und auf Mobilgeraeten ohne Praezisionsklick bedienbar.
