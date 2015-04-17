package li.cil.oc.example.machine;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * This mod demonstrates how to create custom Machines, i.e. computers. To
 * keep it simple, we'll be creating a machine a tile entity, which is easier
 * to get right than one based on entities (may need special chunk unload
 * handling) or even items (needs manual tracking of item stacks "owning" a
 * machine).
 * <p/>
 * The mod tries to keep everything else to a minimum, to focus on the mod-
 * specific parts. It is not intended for use or distribution, but you're free
 * to base a proper addon on this code.
 */
@Mod(modid = "OpenComputers|ExampleMachine",
        name = "OpenComputers Addon Example - Machine",
        version = "1.0.0",
        dependencies = "required-after:OpenComputers@[1.5.0,)")
public class ModExampleMachine {
    @Mod.Instance
    public static ModExampleMachine instance;

    public static BlockMachine machine;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        machine = new BlockMachine();
        GameRegistry.registerBlock(machine, "example_machine");
        GameRegistry.registerTileEntity(TileEntityMachine.class, "oc:example_machine");
    }
}
