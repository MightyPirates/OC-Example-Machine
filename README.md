This is an example mod, demonstrating how to implement a custom Machine, i.e. using OpenComputers to implement a custom computer device.

To run it, after cloning the repository, set up your workspace with gradle as usual:
```
gradlew setupDecompWorkspace idea
```
I recommend enabling the Gradle plugin in IDEA. When opening the project in IDEA with it enabled, it will ask you whether you'd like to import the Gradle project. When you do so, it'll automatically set up the library dependency on the OC API for you.

The example tile entity is basically a preconfigured Microcontroller sans EEPROM (inserted by shift-rightclicking the block with an EEPROM to insert in hand), that can interact with external components. So place it down, insert a Lua BIOS, attach screen, keyboard and disk drive with OpenOS in it. Then right-click the block to run the machine.

The mod is as minimal as possible, while still actually working, so as not to distract from the functionality it is designed to demonstrate. There are no textures, recipes or other details, only a single block to allow creation of the tile entity. Feel free to base a proper addon on the code in this example.

Feel free to submit pull requests to expand and/or clarify documentation!