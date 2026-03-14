Ziel

Die erzeugten Operator-Profil-Dateien sollen vollstaendige, editierbare YAML-Vorlagen enthalten, bei denen auszufuellende Werte explizit als `null` gesetzt sind.

Scope

- Aus den bestehenden Legal-Profilen eine passende Privacy-Template-Datei ableiten.
- Aus den bestehenden Legal-Profilen eine passende Terms-Template-Datei ableiten.
- Auszufuellende Werte explizit mit `null` rendern.
- `expectedContent`-Hinweise als YAML-Kommentare in die erzeugten Vorlagen aufnehmen.

Nicht Teil dieser Subtask

- Dateierzeugung und Entscheidung, ob geschrieben wird.
- ERROR-Logging und Startabbruch.
- Testabdeckung ausserhalb der reinen Template-Struktur.

Akzeptanzkriterien

- Die erzeugte Privacy-Vorlage ist syntaktisch gueltiges YAML.
- Die erzeugte Terms-Vorlage ist syntaktisch gueltiges YAML.
- Pflichtfelder, die Nutzerinnen und Nutzer ausfuellen muessen, stehen auf `null`.
- Die Vorlagen enthalten die benoetigten `expectedContent`-Hinweise als Kommentare in unmittelbarer Naehe der betroffenen Felder.
- Die Vorlagen verwenden die korrekten Profil- und Parent-Bezuege fuer Privacy bzw. Terms.
