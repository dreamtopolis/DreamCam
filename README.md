# 📷 DreamCam – Kamera-System für Minecraft

DreamCam ist ein Kamera-Plugin für Minecraft-Server.  
Spieler können feste Kamerapositionen betreten und zwischen ihnen wechseln.  
Ideal für **Freizeitpark-Server**, **Attraktionen**, **Shows** sowie **Übersichts- oder Beobachtungskameras**.

---

## 🎯 Funktionen

- 📍 Feste Kameras an bestimmten Positionen
- 🗺️ Kameras sind in **Regionen** organisiert
- 📂 Kameras über ein Menü auswählbar
- 🔄 Wechsel zwischen Kameras per Klick
- 👁️ Kamera-Modus mit fixer Position
- 🌙 Nachtsicht im Kamera-Modus
- ⬅️ Verlassen des Kamera-Modus mit **Shift**
- 🌍 Mehrsprachig (Deutsch & Englisch)
- 💾 Kameras bleiben nach Serverneustart erhalten

---

## 📦 Installation

1. Lege die Datei **`DreamCam.jar`** in den Ordner:
/plugins

yaml
Code kopieren
2. Starte den Server
3. Das Plugin erstellt automatisch:
- `config.yml`
- `messages.yml`

Fertig ✅

---

## ⚙️ Sprache einstellen

Öffne die Datei:
plugins/DreamCam/config.yml

go
Code kopieren

```yml
language: de
Mögliche Werte:

de → Deutsch

en → Englisch

Danach im Spiel:

bash
Code kopieren
/camera reload
➡ Alle Texte werden sofort umgestellt

🧪 Befehle
🎥 Kameras verwalten (Admins)
Befehl	Beschreibung
/camera create <name> <region>	Erstellt eine Kamera an deiner aktuellen Position
/camera delete <name>	Löscht eine einzelne Kamera
/camera delete <region>	Löscht alle Kameras einer Region
/camera save	Speichert alle Kameras
/camera load	Lädt Kameras neu
/camera reload	Lädt Einstellungen & Texte neu

📂 Kameras ansehen (Spieler)
Befehl	Beschreibung
/camera menu <region>	Öffnet das Kameramenü einer Region

🎮 Kamera-Modus – Steuerung
Sobald eine Kamera ausgewählt wurde:

👁️ Teleport zur Kamera

🎥 Spectator-Modus aktiv

🌙 Nachtsicht aktiv

🚫 Bewegung gesperrt

👀 Umsehen erlaubt

🔄 Kamera wechseln
Eingabe	Aktion
Rechtsklick	Nächste Kamera
Linksklick	Vorherige Kamera
Shift	Kamera-Modus verlassen

Nach dem Verlassen:

Rückkehr zur vorherigen Position

Ursprünglicher Spielmodus wird wiederhergestellt

🔐 Rechte (Permissions)
Permission	Bedeutung
dreamcam.admin	Kameras erstellen, löschen, speichern & reloaden

Standard: nur OP

💾 Speicherung
Alle Kameras werden automatisch gespeichert.
Nach einem Server-Neustart stehen sie sofort wieder zur Verfügung.

🧩 Typische Einsatzbereiche
🎢 Freizeitpark-Server (Attraktions-Übersichten)

🎆 Show- & Event-Kameras

🏙️ Städte- & Roleplay-Server

🎥 Cinematische Aufnahmen

📡 Beobachtungs- & Info-Kameras

❓ Häufige Fragen
Können mehrere Spieler Kameras nutzen?
Ja, mehrere Spieler können gleichzeitig unterschiedliche Kameras nutzen.

Können Spieler sich bewegen?
Nein. Die Position ist fest, nur das Umsehen ist erlaubt.

Gehen Kameras bei Neustart verloren?
Nein. Alle Kameras werden gespeichert.

Kann ich Texte anpassen?
Ja. Alle Texte befinden sich in der messages.yml.
