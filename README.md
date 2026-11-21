# Life Organizer

Eine Android-App, die Kalender, Aufgaben und Ziele an einem Ort zusammenbringt – entwickelt mit KI als Coding-Partner.

**[⬇️ Neueste Version herunterladen (APK)](https://github.com/milanmeiners/organizer-app/releases/latest)**

## Funktionen

- **Kalender** mit einmaligen und wiederkehrenden Terminen (täglich, wöchentlich, monatlich …)
- **Aufgaben** mit Prioritäten, Deadlines, Filtern und Sortierung
- **Ziele als Baumstruktur:** Hauptziele mit Unterzielen und verknüpften Aufgaben, der Fortschritt wird automatisch berechnet
- **Tagesplaner**, der Termine und Aufgaben eines Tages zusammenführt
- **PDF-Export** der Tages- und Wochenübersicht zum Ausdrucken
- **Backup** – alle Daten bleiben lokal auf deinem Gerät, es gibt keinen Server und kein Konto

## Technik

| Bereich | Umsetzung |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose |
| Datenhaltung | Room (SQLite) |
| Architektur | ViewModel + Repository, getrennte Daten-, Domain- und UI-Schicht |
| Plattform | Android |

## Installation

1. Lade die `.apk`-Datei unter [Releases](https://github.com/milanmeiners/organizer-app/releases/latest) auf dein Android-Handy herunter.
2. Öffne die Datei. Android fragt eventuell, ob du die Installation aus dieser Quelle erlauben möchtest – bestätige das.
3. Fertig – die App erscheint in deiner App-Übersicht.

> Die App ist nicht im Play Store, deshalb fragt Android einmalig nach der Erlaubnis.

## Selbst bauen

Voraussetzung: JDK 17 und das Android SDK (z. B. über Android Studio).

```bash
./gradlew assembleDebug        # APK bauen
./gradlew testDebugUnitTest    # Tests ausführen
```

Die fertige APK liegt danach unter `app/build/outputs/apk/debug/`.

## Projektstruktur

```
app/src/main/java/com/lifeorganizer/app/
├── data/     Datenbank (Room), Entitäten, Repository, Backup
├── domain/   Logik: Wiederholungen, Zielbaum & Fortschritt, Tagesplaner, Aufgabenfilter
├── ui/       Bildschirme und Editoren (Jetpack Compose)
└── print/    PDF-Export
```

## Über das Projekt

Ich wollte eine App, die meinen Alltag so organisiert, wie ich denke: Termine, To-dos und langfristige Ziele hängen zusammen, statt in drei verschiedenen Apps zu liegen. Entstanden ist sie im „Vibe Coding“-Stil – ich habe die Anforderungen formuliert, getestet und iteriert, die KI hat beim Schreiben des Codes geholfen.

---

Mehr Projekte: [milanmeiners.github.io](https://milanmeiners.github.io) · [LinkedIn](https://www.linkedin.com/in/milan-meiners-35820b161/)
