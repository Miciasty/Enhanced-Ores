![Enhanced Ores](images/title.png)
# Enhanced Ores
Enhanced Ores is a Minecraft plugin that allows users to configure custom items to drop from specific blocks with a set drop chance. It also enables the creation and management of regions where these drop mechanics are applied.

Easy management, efficiently and configurably.

## Getting Started

- [Configuration](#configuration)
- [Commands](#commands)
- [Support](#support)
- [Messages Styling](https://docs.advntr.dev/minimessage/format.html)

## Configuration

To configure the Enhanced Ores plugin, edit the config.yml file located in the plugin's directory.

- ### Database Configuration

    This section configures the database connection parameters used by the plugin to store and retrieve data.

    - **dialect** - Specifies the database dialect. For example, ***MySQL8Dialect*** for MySQL.
    - **address** - The IP address or hostname of the database server.
    - **port** - The port on which the database server is listening. 
    - **database** - The name of the database.
    - **username** - The username used to connect to the database.
    - **password** - The password used to connect to the database.
    <br></br>
    ```yml
    database:
        dialect:    MySQL8Dialect
        address:    localhost
        port:       3306
        database:   enhanced_ores
        username:   root
        password:   ""
    ```

- ### Hibernate Configuration
  
    This section contains Hibernate-specific settings.

    - **show_sql** - If set to true, SQL statements will be logged to the console.
    - **format_sql** - If set to true, SQL statements will be formatted.
    - **sql_comments** - If set to true, SQL comments will be included in the logged SQL statements.
    <br></br>
    ```yml
    hibernate:
        show_sql:       false
        format_sql:     true
        sql_comments:   true
    ```
  
- ### Economy Item

    This section configures the item that will be dropped from the specified ores.

    - **material** - The material type of the item.
    - **item-meta** - Contains metadata for the item.
    - **display-name** - The display name of the item.
    - **lore** - A list of lore lines for the item.
    <br></br>
    ```yml
    economy-item:
        material: GOLD_NUGGET
        item-meta:
            display-name: <!italic><gold>Golden coin
            lore:
                - <gray>[-] </gray><yellow>Server currency</yellow>
                - <gray>[-] </gray><green>Usage -> </green><dark_green>Paying for plots, buying items.</dark_green>
    ```

- ### Ores Configuration

    This section specifies which ores can drop the configured economy item.

    - **ores** - A list of ores from which the economy item can drop. Only blocks whose names end with **_ORE** can be used.
    <br></br>
    ```yml
    ores:
        - DEEPSLATE_GOLD
        - GOLD
        - IRON
        - ...
    ```

- ### Drop Configuration

    This section configures the drop chance for the economy item and cooldown.

    - **drop-chance** - The chance (in percentage) for the economy item to drop. It can be set as an integer, float, or double. If the value is less than 1, it will be multiplied by 100. The minimum value is 0 (0%) and the maximum value is 100 (100%).
    - **cooldown** - The time (in milliseconds) required before the same block can be mined again. It can be set as an integer, float, or double.
    <br></br>
    ```yml
    drop-chance: 25
  
    cooldown: 2000
    ```

## Commands

- `/eo help` Shows all existing commands

- `/eo list` Shows all existing regions showing their ID and Name

- `/eo region **check**` Checks player's location and searching if any region contains it. If true, sending message with information about ID, Name, World, PointA, PointB of the Region.

- `/eo region **open** < id / name >` Opening new session for Player if region exist, if not then the plugin will create a new one with given name.

> [!NOTE]
> When Player has active session his every action, which are **[LEFT_CLICK]** and **[RIGHT_CLICK]** will be seen as new points A and B of the Region.

- `/eo region **close**` Closes Player's active session.

- `/eo region **remove** < id / name >` Removes existing region.

## Support

For questions about Enhanced Ores, first try the ~~[Wiki](https://example.com/)~~ to see if your question is already answered there.
If you can't find what you're looking for, contact me via Discord ***.n.u*** or ***❤ Miciasty ❤***
