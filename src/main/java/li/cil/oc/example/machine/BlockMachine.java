package li.cil.oc.example.machine;

import li.cil.oc.api.Items;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockMachine extends Block {
    public BlockMachine() {
        super(Material.anvil);
        setCreativeTab(CreativeTabs.tabAllSearch);
        setBlockName("Machine");
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileEntityMachine();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (Items.get(player.getHeldItem()) == Items.get("eeprom")) {
                final TileEntity tileEntity = world.getTileEntity(x, y, z);
                if (tileEntity instanceof TileEntityMachine) {
                    if (!world.isRemote) {
                        final TileEntityMachine machine = (TileEntityMachine) tileEntity;
                        final ItemStack stack = player.inventory.decrStackSize(player.inventory.currentItem, 1);
                        if (stack != null && stack.stackSize > 0) {
                            final ItemStack oldStack = machine.getStackInSlot(0);
                            machine.setInventorySlotContents(0, stack);
                            if (player.inventory.addItemStackToInventory(oldStack)) {
                                player.inventory.markDirty();
                                if (player.openContainer != null) {
                                    player.openContainer.detectAndSendChanges();
                                }
                            }
                            if (oldStack != null && oldStack.stackSize > 0) {
                                player.dropPlayerItemWithRandomChoice(oldStack, false);
                            }
                        }
                    }
                    return true;
                }
            }
        }
        else {
            final TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TileEntityMachine) {
                if (!world.isRemote) {
                    final TileEntityMachine machine = (TileEntityMachine) tileEntity;
                    machine.machine().start();
                }
                return true;
            }
        }
        return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!world.isRemote) {
            final TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TileEntityMachine) {
                final TileEntityMachine machine = (TileEntityMachine) tileEntity;
                for (int i = 0; i < machine.getSizeInventory(); ++i) {
                    final ItemStack stack = machine.getStackInSlot(i);
                    if (stack != null) {
                        final EntityItem entity = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack);
                        world.spawnEntityInWorld(entity);
                    }
                }
            }
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }
}
