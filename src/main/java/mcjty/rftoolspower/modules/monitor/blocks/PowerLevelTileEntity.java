package mcjty.rftoolspower.modules.monitor.blocks;


import mcjty.lib.blocks.LogicSlabBlock;
import mcjty.lib.builder.BlockBuilder;
import mcjty.lib.tileentity.LogicTileEntity;
import mcjty.lib.varia.EnergyTools;
import mcjty.rftoolspower.compat.RFToolsPowerTOPDriver;
import mcjty.rftoolspower.modules.monitor.MonitorModule;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static mcjty.lib.builder.TooltipBuilder.header;
import static mcjty.lib.builder.TooltipBuilder.key;

public class PowerLevelTileEntity extends LogicTileEntity implements ITickableTileEntity {

    public PowerLevelTileEntity() {
        super(MonitorModule.TYPE_POWER_LEVEL.get());
    }

    private int counter = 20;

    public static LogicSlabBlock createBlock() {
        return new LogicSlabBlock(new BlockBuilder()
                .topDriver(RFToolsPowerTOPDriver.DRIVER)
                .info(key("message.rftoolsutility.shiftmessage"))
                .infoShift(header())
                .tileEntitySupplier(PowerLevelTileEntity::new));
    }

    @Nonnull
    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.getUpdateTag();
        tag.putInt("power", getPowerOutput());
        return tag;
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        super.handleUpdateTag(state, tag);
        powerOutput = tag.getInt("power");
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(worldPosition, 1, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        handleUpdateTag(getBlockState(), pkt.getTag());
    }

    @Override
    public void tick() {
        if (level.isClientSide) {
            return;
        }

        counter--;
        if (counter > 0) {
            return;
        }
        counter = 20;

        Direction inputSide = getFacing(level.getBlockState(getBlockPos())).getInputSide();
        BlockPos inputPos = getBlockPos().relative(inputSide);
        TileEntity tileEntity = level.getBlockEntity(inputPos);
        if (!EnergyTools.isEnergyTE(tileEntity, null)) {
            setRedstoneState(0);
            return;
        }
        EnergyTools.EnergyLevel energy = EnergyTools.getEnergyLevelMulti(tileEntity, null);
        long maxEnergy = energy.getMaxEnergy();
        int ratio = 0;

        if (maxEnergy > 0) {
            long stored = energy.getEnergy();
            ratio = (int) (stored * 10 / maxEnergy);
            if (ratio < 0) {
                ratio = 0;
            } else if (ratio > 9) {
                ratio = 9;
            }
        }
        setRedstoneState(ratio);
    }
}
