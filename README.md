# Soul Link Plugin

A Paper Spigot 1.21.11 plugin that implements Soul Link mode - a challenging gameplay mode where players' health is shared.

## Features

### Core Gameplay
- **Player Linking**: Up to 4 players are automatically linked when they join the server
- **Shared Damage**: When one player takes damage, all linked players take the same damage
- **Shared Healing**: When one player heals, all linked players heal the same amount
- **Hardcore Mode**: Game is played in hardcore difficulty with high stakes
- **Spectator Mode**: 5th and additional players automatically become spectators
- **Game Over**: If any linked player dies, the game ends for all players

### Visual Feedback
- **Action Bar Messages**: Real-time damage and healing notifications displayed on the action bar
- **Locate Bar**: Compass targeting system to help players locate their linked teammates

### Game Settings
Access the settings GUI with `/settings` command:

1. **Natural Regeneration Toggle**
   - Enable/disable natural health regeneration
   - Default: Enabled

2. **Last Chance System**
   - Number of times a player can be saved from death (0-2)
   - When triggered, player is saved with 1 health
   - Default: 1 last chance
   - Left click to increase, right click to decrease

3. **Locate Bar Toggle**
   - Enable/disable compass targeting to linked players
   - Helps players find each other across the map
   - Default: Enabled

## Installation

1. Download the latest release JAR file
2. Place it in your server's `plugins` folder
3. Restart your server
4. The plugin will automatically link players as they join (up to 4)

## Building from Source

```bash
mvn clean package
```

The compiled JAR will be in the `target` directory.

## Requirements

- Paper Spigot 1.21.1 or higher
- Java 21 or higher

## Permissions

- `soullink.settings` - Access to the settings command (default: op)

## Commands

- `/settings` - Opens the settings GUI to configure game options

## How It Works

1. When the server starts, players who join (up to 4) are automatically linked
2. All linked players have their damage and healing synchronized
3. Action bar messages show when damage is taken or healing is received
4. The locate bar (compass) helps players find each other
5. If a player would die but has last chances remaining, they are saved with 1 health
6. If a player dies without last chances, the game ends for all players
7. Players who join after the 4-player limit become spectators

## Configuration

All configuration is done through the in-game GUI (`/settings` command). Settings include:
- Natural regeneration on/off
- Last chance count (0-2)
- Locate bar on/off

## Technical Details

### Architecture
- **SoulLinkPlugin**: Main plugin class, handles initialization
- **GameManager**: Core game logic, manages player linking and game state
- **PlayerListener**: Handles player events (join, quit, damage, healing, death)
- **SettingsCommand**: Command executor for the `/settings` command
- **SettingsGUI**: Interactive GUI for game settings

### Key Mechanics
- Damage and healing are shared in real-time using event listeners
- Last chance system intercepts death events and respawns players
- Locate bar uses compass targeting to point to linked players
- Game state is managed centrally in the GameManager

## License

This project is licensed under the MIT License.

## Credits

Developed by Axeloune for Paper Spigot 1.21.11
