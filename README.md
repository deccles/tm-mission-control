# TM Companion

Second-screen helper for the official Terraforming Mars digital client. It tails Unity `Player.log` and shows the **opponent board** (cubes, production, tags, blue cards) plus a sticky reminder when a card leads into tile placement.

It only reconstructs public information from the local log. Hidden hands are never shown. Intended for local vs AI.

## Run

Java 21 and Maven 3.9+.

```bash
mvn package
java -jar target/tm-companion-1.0.0.jar
```

Or double-click `start.bat`. A browser tab opens at [http://127.0.0.1:8765/](http://127.0.0.1:8765/). Leave it on a second monitor while you play.

Default log path:

`%USERPROFILE%\AppData\LocalLow\LuckyHammers\Terraforming Mars\Player.log`

Override with `--log` / `--port` if needed.

## What it tracks

- Opponent and your M€ / steel / titanium / plants / energy / heat (quantity + production)
- Tags, blue cards, automated cards, events
- TR, cities on Mars, greeneries, oceans
- Milestones and awards
- The card currently being played, including placement tips (Capital oceans, Research Outpost isolation, Mohole, etc.)

Cube counts during the production animation can lag by a moment; production values update when cards are played.
