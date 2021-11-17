package mcjty.rftoolspower.modules.powercell.blocks;

import mcjty.lib.blocks.BaseBlock;
import mcjty.lib.blocks.RotationType;
import mcjty.lib.builder.BlockBuilder;
import mcjty.lib.crafting.INBTPreservingIngredient;
import mcjty.rftoolspower.compat.RFToolsPowerTOPDriver;
import mcjty.rftoolspower.modules.powercell.PowerCellConfig;
import mcjty.rftoolspower.modules.powercell.PowerCellModule;
import mcjty.rftoolspower.modules.powercell.data.Tier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static mcjty.lib.builder.TooltipBuilder.*;

public class PowerCellBlock extends BaseBlock implements INBTPreservingIngredient {

    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final BooleanProperty LOWER = BooleanProperty.create("lower");

    @Override
    public RotationType getRotationType() {
        return RotationType.NONE;
    }

    public PowerCellBlock(Tier tier) {
        super(new BlockBuilder()
                .topDriver(RFToolsPowerTOPDriver.DRIVER)
                .info(key("message.rftoolspower.shiftmessage"))
                .infoShift(header(),
                        parameter("info", stack -> {
                            long power = 0;
                            CompoundNBT tagCompound = stack.getTag();
                            if (tagCompound != null) {
                                power = tagCompound.getLong("Energy");
                            }

                            long totpower = 0;
                            if (stack.getItem() == PowerCellModule.CELL1_ITEM.get()) {
                                totpower = PowerCellTileEntity.safeCast(PowerCellConfig.TIER1_MAXRF.get());
                            } else if (stack.getItem() == PowerCellModule.CELL2_ITEM.get()) {
                                totpower = PowerCellTileEntity.safeCast(PowerCellConfig.TIER2_MAXRF.get());
                            } else if (stack.getItem() == PowerCellModule.CELL3_ITEM.get()) {
                                totpower = PowerCellTileEntity.safeCast(PowerCellConfig.TIER3_MAXRF.get());
                            }
                            return Long.toString(power) + " (max " + Long.toString(totpower) + " RF/FE)";
                        }))
                .tileEntitySupplier(() -> new PowerCellTileEntity(tier)));
    }

    @Override
    public void setPlacedBy(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nullable LivingEntity placer, @Nonnull ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide) {

            TileEntity te = world.getBlockEntity(pos);
            if (te instanceof PowerCellTileEntity) {
                PowerCellTileEntity powercell = (PowerCellTileEntity) te;
                long energy = stack.hasTag() ? stack.getTag().getLong("energy") : 0;
                powercell.setLocalEnergy(energy);
                powercell.getNetwork();   // Force a rebuild of the network
                powercell.markDirtyQuick();
            }
        }
    }

//    @Override
//    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess access, BlockPos pos, BlockState metadata, int fortune) {
//        super.getDrops(drops, access, pos, metadata, fortune);
//        TileEntity te = access.getTileEntity(pos);
//        if (te instanceof PowerCellTileEntity) {
//            PowerCellTileEntity powercell = (PowerCellTileEntity) te;
//            powercell.redistributeNetwork();
//            long energy = powercell.getLocalEnergy();
//            if (!drops.isEmpty()) {
//                CompoundNBT tagCompound = drops.get(0).getTagCompound();
//                if (tagCompound == null) {
//                    tagCompound = new CompoundNBT();
//                    drops.get(0).setTagCompound(tagCompound);
//                }
//                tagCompound.setLong("energy", energy);
//            }
//        }
//    }


    @Override
    protected boolean wrenchUse(World world, BlockPos pos, Direction side, PlayerEntity player) {
        if (!world.isClientSide) {
            TileEntity te = world.getBlockEntity(pos);
            if (te instanceof PowerCellTileEntity) {
                PowerCellTileEntity powerCellTileEntity = (PowerCellTileEntity) te;
                powerCellTileEntity.toggleMode(side);
            }
        }
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        BlockState state = super.getStateForPlacement(context);
        World world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return state.setValue(UPPER, world.getBlockState(pos.above()).getBlock() == this)
                .setValue(LOWER, world.getBlockState(pos.below()).getBlock() == this);
    }

    @SuppressWarnings("deprecation")
    @Nonnull
    @Override
    public BlockState updateShape(@Nonnull BlockState stateIn, @Nonnull Direction facing, @Nonnull BlockState facingState, @Nonnull IWorld world, @Nonnull BlockPos pos, @Nonnull BlockPos facingPos) {
        if (facing == Direction.UP) {
            return stateIn.setValue(UPPER, facingState.getBlock() == this);
        }
        if (facing == Direction.DOWN) {
            return stateIn.setValue(LOWER, facingState.getBlock() == this);
        }
        return stateIn;
    }

    //    @Override
//    public BlockState getExtendedState(BlockState state, IBlockReader world, BlockPos pos) {
//        return state.with(UPPER, world.getBlockState(pos.up()).getBlock() == this)
//                .with(LOWER, world.getBlockState(pos.down()).getBlock() == this);
//    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateContainer.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UPPER, LOWER);
    }

    @Override
    @Nonnull
    public BlockRenderType getRenderShape(@Nonnull BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public Collection<String> getTagsToPreserve() {
        return Collections.singleton("BlockEntityTag");
    }
}
