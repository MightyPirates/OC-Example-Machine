package li.cil.oc.example.machine;

import li.cil.oc.api.Driver;
import li.cil.oc.api.Items;
import li.cil.oc.api.driver.Item;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.prefab.TileEntityEnvironment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import scala.actors.threadpool.Arrays;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TileEntityMachine extends TileEntityEnvironment implements IInventory, MachineHost {
    private Machine machine;

    // Track actual item stacks. We'll have most of the components pre-
    // configured in this case, except for the EEPROM which must be inserted
    // manually. This could just as well be a completely dynamic list, as
    // long as the components for the items are properly created and destroyed
    // whenever an item is added/removed (which in this example implementation
    // would be no additional effort whatsoever, by the way).
    private ItemStack[] inventory = new ItemStack[]{
            null, // Reserved for EEPROM / IInventory
            Items.get("cpu1").createItemStack(1),
            Items.get("ram2").createItemStack(1),
            Items.get("ram2").createItemStack(1),
            Items.get("graphicsCard1").createItemStack(1)
    };

    // The list of currently active components, one for each item installed.
    // These are mapped by index to the item stacks (i.e. same indices belong
    // together). Filled in when created/loaded/initialized.
    private ManagedEnvironment[] components = new ManagedEnvironment[inventory.length];

    // The list of *updating* components. Components that need to be updated
    // each tick are managed here. Avoids looping over the full list of
    // components each tick.
    private List<ManagedEnvironment> updatingComponents = new ArrayList<ManagedEnvironment>(components.length);

    @Override
    public Node node() {
        // Expose the machine's node as our own. This will automatically
        // connect the machine to the node network, and allow it to use
        // connected block components, for example. We do not wish to
        // use the machine() getter here, to avoid creating an instance
        // if this is called on the client side.
        return machine != null ? machine.node() : null;
    }

    // ----------------------------------------------------------------------- //

    // I'd recommend using onConnect/onDisconnect for initialization and
    // disposal logic, as that's also what's used by components. You're
    // responsible for connecting the components to the machine after the
    // world is available, and for disconnecting all components when the
    // tile entity gets destroyed or unloaded.

    @Override
    public void onConnect(Node node) {
        super.onConnect(node);
        if (node == node()) {
            // When the machine is connected to the world, connect all
            // components, because that's how they will know they are now
            // in the world (i.e. have access to a world object).
            for (int i = 0; i < inventory.length; ++i) {
                // Make sure the component has been created. This is necessary
                // if the tile entity was created for the first time, since
                // we don't want to create the components before this, as they
                // may be overwritten by the ones created when loading.
                createComponent(i, inventory[i]);

                if (components[i] != null) {
                    node.connect(components[i].node());
                }
            }
        }
    }

    @Override
    public void onDisconnect(Node node) {
        super.onDisconnect(node);
        if (node == node()) {
            // When the machine gets disconnected (unloaded), disconnect all
            // components, because that's how they will know they have to
            // clean up (e.g. remove themselves from the wireless network).
            // Note that this is called on invalidate and onChunkUnload,
            // because our main node gets disconnected by the parent class
            // in these cases!
            for (ManagedEnvironment environment : components) {
                if (environment != null) {
                    environment.node().remove();
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    // Updating the machine and components is pretty straight-forward. You
    // should do the first update after the tick the machine got connected
    // in, though, just to make sure the complete network has been rebuilt
    // before the update (otherwise the machine will quickly disconnect those
    // not yet loaded components, then reconnect them in the next tick).

    @Override
    public void updateEntity() {
        // Wait with the first machine update until the machine has been
        // connected to the network. Also skip the first tick, to ensure
        // the complete network has been rebuilt, to avoid losing components
        // that are actually still there, just haven't been reconnected yet.
        if (!worldObj.isRemote && machine().node().network() != null) {
            machine().update();

            for (ManagedEnvironment environment : updatingComponents) {
                environment.update();
            }
        }

        super.updateEntity();
    }

    // ----------------------------------------------------------------------- //

    // Loading and saving. These are relatively simple, just make sure to
    // save the components to the item stacks before saving the item stacks,
    // and make sure to load the items before loading the machine.

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        // Load items first, so that the machine can access them when loading.
        final NBTTagList itemsNbt = nbt.getTagList("items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < Math.min(inventory.length, itemsNbt.tagCount()); ++i) {
            // Inventory loading.
            final NBTTagCompound stackNbt = itemsNbt.getCompoundTagAt(i);
            final ItemStack stack = ItemStack.loadItemStackFromNBT(stackNbt);
            inventory[i] = stack;

            // Create components, but don't connect them yet. They will be once
            // the machine gets connected (see onConnect).
            final Item driver = Driver.driverFor(stack, getClass());
            if (stack != null && driver != null) {
                final ManagedEnvironment environment = driver.createEnvironment(stack, this);
                if (environment != null) {
                    environment.load(driver.dataTag(stack));
                }
                components[i] = environment;
                if (environment != null && environment.canUpdate()) {
                    updatingComponents.add(environment);
                }
            }
        }

        // Load the machine *after* the items, so that it can use them if necessary.
        machine().load(nbt.getCompoundTag("machine"));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        final NBTTagList itemsNbt = new NBTTagList();
        for (int i = 0; i < inventory.length; ++i) {
            final ItemStack stack = inventory[i];

            // Save components to items, first, so the info gets saved with the items.
            final ManagedEnvironment environment = components[i];
            final Item driver = Driver.driverFor(stack, getClass());
            if (stack != null && environment != null && driver != null) {
                environment.save(driver.dataTag(stack));
            }

            // Inventory saving.
            final NBTTagCompound stackNbt = new NBTTagCompound();
            if (stack != null) {
                stack.writeToNBT(stackNbt);
            }
            itemsNbt.appendTag(stackNbt);
        }
        nbt.setTag("items", itemsNbt);

        // Machine could be saved before the components... I think. But let's
        // keep it consistent with the loading code.
        final NBTTagCompound machineNbt = new NBTTagCompound();
        machine().save(machineNbt);
        nbt.setTag("machine", machineNbt);
    }

    // ----------------------------------------------------------------------- //

    // This is just your everyday inventory implementation, not much to really
    // see here, except for the onItemAdded/onItemRemoved calls at the
    // appropriate places, which will cause dynamic components to be added and
    // removed correctly (see below).

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < getSizeInventory() && inventory[slot] != null
                ? inventory[slot].copy()
                : null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot >= 0 && slot < getSizeInventory()) {
            if (inventory[slot] != null) {
                onItemRemoved(slot, inventory[slot] = null);
            }
            if (stack != null) {
                final ItemStack newStack = stack.copy();
                inventory[0] = newStack;
                onItemAdded(slot, newStack);
            }
        }
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot >= 0 && slot < getSizeInventory()) {
            final ItemStack stack = inventory[slot];
            if (stack != null) {
                final int removed = Math.min(stack.stackSize, amount);
                stack.stackSize -= removed;
                if (stack.stackSize < 1) {
                    inventory[slot] = null;
                    onItemRemoved(slot, stack);
                }
                if (removed > 0) {
                    final ItemStack result = stack.copy();
                    result.stackSize = removed;
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public String getInventoryName() {
        return null;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && Items.get(stack) == Items.get("eeprom");
    }

    // ----------------------------------------------------------------------- //

    // This is what's required of classes "hosting" a machine. The methods
    // should all be fairly self-explanatory, otherwise check their Javadoc.

    @Override
    public Machine machine() {
        if (machine == null) {
            machine = li.cil.oc.api.Machine.create(this);
        }
        return machine;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<ItemStack> internalComponents() {
        return Arrays.asList(inventory);
    }

    @Override
    public int componentSlot(String address) {
        for (int i = 0; i < components.length; ++i) {
            final ManagedEnvironment environment = components[i];
            if (environment != null && environment.node() != null && Objects.equals(environment.node().address(), address)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onMachineConnect(Node node) {
        // This is called when the machine's `onConnect` is called. It allows
        // hooking into that event from a machine's host, so that the host can
        // directly expose the machine's node as its own (as we're doing here).
        this.onConnect(node);
    }

    @Override
    public void onMachineDisconnect(Node node) {
        // Same as for `onMachineConnect`.
        this.onDisconnect(node);
    }

    // ----------------------------------------------------------------------- //

    // General location awareness of the machine. This is used for a number of
    // things, such as determining where to save the machine's state in the
    // `opencomputers/state` directory structure. The position should be the
    // center of the owner of the machine (so for an entity it'd be the entity's
    // actual position, for example).

    @Override
    public World world() {
        return worldObj;
    }

    @Override
    public double xPosition() {
        return xCoord + 0.5;
    }

    @Override
    public double yPosition() {
        return yCoord + 0.5;
    }

    @Override
    public double zPosition() {
        return zCoord + 0.5;
    }

    @Override
    public void markChanged() {
        // This is called when the state of the machine changed, not just its
        // "run state", but actual internal state, to let the owner know that
        // it needs saving when the world next saves. For entities this is
        // mostly irrelevant, because they're always saved, anyway.
        if (worldObj != null) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
        }
    }

    // ----------------------------------------------------------------------- //

    // Generic methods for updating components, to make it easier to make
    // additional slots dynamic (not just the EEPROM one). What they do should
    // be pretty clear. When removing, we disconnect the existing components,
    // then save them to their stack. When adding we create the component
    // and load it from the stack.

    private void createComponent(int slot, ItemStack stack) {
        // This check is just needed to make it simpler to use in onConnect.
        if (stack == null || components[slot] != null) return;

        // Get the driver for the component, so we can create an environment.
        // You don't have to pass the host class here, but it's the preferred
        // way, since that allows for component blacklisting by host.
        final Item driver = Driver.driverFor(stack, getClass());
        if (driver != null) {
            final ManagedEnvironment environment = driver.createEnvironment(stack, this);
            if (environment != null) {
                environment.load(driver.dataTag(stack));
                components[slot] = environment;
                if (components[slot].canUpdate()) {
                    updatingComponents.add(components[slot]);
                }
            }
        }
    }

    private void onItemAdded(int slot, ItemStack stack) {
        createComponent(slot, stack);
        if (components[slot] != null) {
            // Connect it to the network, allowing it to be used. Internal
            // components should always be connected directly to the
            // machine, since some are set to neighbor visibility (such as
            // graphics cards / cards in general), and won't work otherwise.
            machine().node().connect(components[slot].node());
        }
    }

    private void onItemRemoved(int slot, ItemStack stack) {
        final ManagedEnvironment environment = components[slot];
        if (environment != null) {
            if (environment.node() != null) {
                // Remove the node from the network, giving it a chance to
                // clean up before we're saving it. File systems use this
                // to close handles, for example.
                environment.node().remove();
            }

            // Get the driver to get the data tag; this should always be
            // non-null, otherwise we wouldn't have an environment, but...
            final Item driver = Driver.driverFor(stack, getClass());
            if (driver != null) {
                environment.save(driver.dataTag(stack));
            }

            if (environment.node() != null) {
                // This is required for components that create their own
                // network when saving. Some components do this, to ensure
                // they have an address, e.g. text buffers, to make sure
                // that address can be sent to clients.
                environment.node().remove();
            }

            components[slot] = null;
            updatingComponents.remove(environment);
        }
    }
}
