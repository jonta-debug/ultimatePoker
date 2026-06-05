# Ultimate Poker

Ultimate Poker is a small Java Swing desktop project that simulates a poker round with multiple players, community cards, chip visuals, and automatic hand evaluation.

## Features

- Configurable round setup for 2 to 9 players
- Custom player names, starting money, and entry bet
- Step-by-step reveal of community cards and player hole cards
- Automatic winner detection based on evaluated poker hands
- Card, deck, and chip graphics loaded from local sprite assets

## Project Structure

- `src/` - Java source files for game logic, hand evaluation, assets, and Swing UI
- `bin/` - compiled class files
- `Poker cards 1.3/` - image assets used by the interface

## Run

Compile:

```bash
javac -d bin src/*.java
```

Start:

```bash
java -cp bin App
```

## Notes

- Main entry point: `src/App.java`
- The current UI uses some German labels in the setup and game screens
