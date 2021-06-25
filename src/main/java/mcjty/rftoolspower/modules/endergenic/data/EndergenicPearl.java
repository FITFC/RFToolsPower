package mcjty.rftoolspower.modules.endergenic.data;

import mcjty.lib.varia.BlockPosTools;
import mcjty.lib.varia.Logging;
import mcjty.rftoolspower.modules.endergenic.blocks.EndergenicTileEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EndergenicPearl {
    private int ticksLeft;
    private final BlockPos destination;
    private final int age;

    public EndergenicPearl(int ticksLeft, BlockPos destination, int age) {
        this.ticksLeft = ticksLeft;
        this.destination = destination;
        this.age = age;
    }

    public EndergenicPearl(CompoundNBT tagCompound) {
        ticksLeft = tagCompound.getInt("t");
        destination = BlockPosTools.read(tagCompound, "dest");
        age = tagCompound.getInt("age");
    }

    public int getTicksLeft() {
        return ticksLeft;
    }

    public int getAge() {
        return age;
    }

    public BlockPos getDestination() {
        return destination;
    }

    // Return true if the pearl has to be removed (it arrived).
    public boolean handleTick(World world) {
        ticksLeft--;
        if (ticksLeft <= 0) {
            // We arrived. Check that the destination is still there.
            TileEntity te = world.getBlockEntity(destination);
            if (te instanceof EndergenicTileEntity) {
                EndergenicTileEntity endergenicTileEntity = (EndergenicTileEntity) te;
                endergenicTileEntity.receivePearl(age);
            } else {
                Logging.log("Pearl: where did the destination go?");
            }
            return true;
        }
        return false;
    }

    public CompoundNBT getTagCompound() {
        CompoundNBT tagCompound = new CompoundNBT();
        tagCompound.putInt("t", ticksLeft);
        BlockPosTools.write(tagCompound, "dest", destination);
        tagCompound.putInt("age", age);
        return tagCompound;
    }
}
