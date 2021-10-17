package mcjty.rftoolspower.modules.dimensionalcell;

import mcjty.lib.varia.Logging;
import mcjty.lib.varia.OrientationTools;
import mcjty.lib.varia.LevelTools;
import mcjty.lib.worlddata.AbstractWorldData;
import mcjty.rftoolspower.RFToolsPower;
import mcjty.rftoolspower.compat.RFToolsDimensionChecker;
import mcjty.rftoolspower.modules.dimensionalcell.blocks.DimensionalCellBlock;
import mcjty.rftoolspower.modules.dimensionalcell.blocks.DimensionalCellType;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.*;

public class DimensionalCellNetwork extends AbstractWorldData<DimensionalCellNetwork> {

    private static final String DIMENSIONALCELL_NETWORK_NAME = "RFToolsDimensionalCellNetwork";

    private int lastId = 0;

    private final Map<Integer,Network> networks = new HashMap<>();

    public DimensionalCellNetwork() {
        super(DIMENSIONALCELL_NETWORK_NAME);
    }

    public static DimensionalCellNetwork get(World world) {
        return getData(world, DimensionalCellNetwork::new, DIMENSIONALCELL_NETWORK_NAME);
    }

    public Network getOrCreateNetwork(int id) {
        Network channel = networks.get(id);
        if (channel == null) {
            channel = new Network();
            networks.put(id, channel);
        }
        return channel;
    }

    public Network getChannel(int id) {
        return networks.get(id);
    }

    public void deleteChannel(int id) {
        networks.remove(id);
    }

    public int newChannel() {
        lastId++;
        return lastId;
    }

    @Override
    public void load(CompoundNBT tagCompound) {
        networks.clear();
        ListNBT lst = tagCompound.getList("networks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0 ; i < lst.size() ; i++) {
            CompoundNBT tc = lst.getCompound(i);
            int channel = tc.getInt("channel");
            Network value = new Network();
            value.readFromNBT(tc);
            networks.put(channel, value);
        }
        lastId = tagCompound.getInt("lastId");
    }

    @Override
    public CompoundNBT save(CompoundNBT tagCompound) {
        ListNBT lst = new ListNBT();
        for (Map.Entry<Integer, Network> entry : networks.entrySet()) {
            CompoundNBT tc = new CompoundNBT();
            tc.putInt("channel", entry.getKey());
            entry.getValue().writeToNBT(tc);
            lst.add(tc);
        }
        tagCompound.put("networks", lst);
        tagCompound.putInt("lastId", lastId);
        return tagCompound;
    }

    public static class Network {
        private int energy = 0;
        private Set<GlobalPos> blocks = new HashSet<>();
        private int simpleBlocks = 0;
        private int advancedBlocks = 0;

        // Connectivity information that calculates the cost of extracting energy depending
        // on distance factors. Is recalculated automatically if it is null.
        private Map<GlobalPos, Float> costFactor = null;

        // Be careful with this! Don't modify the set
        public Set<GlobalPos> getBlocks() {
            return blocks;
        }

        public int getBlockCount() {
            return blocks.size();
        }

        public int getAdvancedBlockCount() {
            return advancedBlocks;
        }

        public int getSimpleBlockCount() {
            return simpleBlocks;
        }

        public int calculateMaximumEnergy() {
            long totEnergyLong = (long) DimensionalCellConfiguration.rfPerNormalCell.get() * (getBlockCount() - getAdvancedBlockCount() - getSimpleBlockCount())
                    + (long) DimensionalCellConfiguration.rfPerNormalCell.get() * DimensionalCellConfiguration.advancedFactor.get() * getAdvancedBlockCount()
                    + ((long) DimensionalCellConfiguration.rfPerNormalCell.get() * getSimpleBlockCount() / DimensionalCellConfiguration.simpleFactor.get());
            if (totEnergyLong > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) totEnergyLong;
        }

        public void updateNetwork(World w) {
            advancedBlocks = 0;
            simpleBlocks = 0;
            Iterable<GlobalPos> copy = new HashSet<>(blocks);
            blocks.clear();
            for (GlobalPos c : copy) {
                World world = LevelTools.getLevel(c.dimension());
                BlockState state = world.getBlockState(c.pos());
                if (state.getBlock() == DimensionalCellModule.DIMENSIONAL_CELL.get()) {
                    blocks.add(c);
                } else if (DimensionalCellBlock.getType(state.getBlock()).isAdvanced()) {
                    blocks.add(c);
                    advancedBlocks++;
                } else if (DimensionalCellBlock.getType(state.getBlock()).isSimple()) {
                    blocks.add(c);
                    simpleBlocks++;
                } else {
                    Logging.log("Warning! Powercell network data was not up-to-date!");
                }
            }

        }

        public void add(World world, GlobalPos g, DimensionalCellType type) {
            if (!blocks.contains(g)) {
                blocks.add(g);
                costFactor = null;
                if (type.isAdvanced()) {
                    advancedBlocks++;
                }
                if (type.isSimple()) {
                    simpleBlocks++;
                }
                updateNetwork(world);
            }
        }

        public void remove(World world, GlobalPos g, DimensionalCellType type) {
            if (blocks.contains(g)) {
                blocks.remove(g);
                costFactor = null;
                if (type.isAdvanced()) {
                    advancedBlocks--;
                }
                if (type.isSimple()) {
                    simpleBlocks--;
                }
                updateNetwork(world);
            }
        }

        private double calculateBlobDistance(World world, Set<GlobalPos> blob1, Set<GlobalPos> blob2) {
            GlobalPos c1 = blob1.iterator().next();
            GlobalPos c2 = blob2.iterator().next();

            // @todo 1.14 rftools dimensions!
            boolean dim1rftools = RFToolsPower.setup.rftoolsDimensions && RFToolsDimensionChecker.isRFToolsDimension(world, LevelTools.getLevel(c1.dimension()));
            boolean dim2rftools = RFToolsPower.setup.rftoolsDimensions && RFToolsDimensionChecker.isRFToolsDimension(world, LevelTools.getLevel(c2.dimension()));
            double rftoolsdimMult = 1.0;
            if (dim1rftools) {
                rftoolsdimMult *= DimensionalCellConfiguration.powerCellRFToolsDimensionAdvantage.get();
            }
            if (dim2rftools) {
                rftoolsdimMult *= DimensionalCellConfiguration.powerCellRFToolsDimensionAdvantage.get();
            }

            if (!c1.dimension().equals(c2.dimension())) {
                return DimensionalCellConfiguration.powerCellDistanceCap .get()* rftoolsdimMult;
            }
            double dist = Math.sqrt(c1.pos().distSqr(c2.pos()));
            if (dist > DimensionalCellConfiguration.powerCellDistanceCap.get()) {
                dist = DimensionalCellConfiguration.powerCellDistanceCap.get();
            } else if (dist < DimensionalCellConfiguration.powerCellMinDistance.get()) {
                dist = DimensionalCellConfiguration.powerCellMinDistance.get();
            }
            return dist * rftoolsdimMult;
        }

        private void updateCostFactor(World world) {
            if (costFactor == null) {
                costFactor = new HashMap<>();
                // Here we calculate the different blobs of powercells (all connected cells)
                List<Set<GlobalPos>> blobs = new ArrayList<>();
                getBlobs(blobs);

                // For every blob we calculate it's 'strength' relative to the other blobs.
                for (Set<GlobalPos> blob : blobs) {

                    double totalfactor = 1.0f;

                    // Scan all blobs different from this one
                    for (Set<GlobalPos> blob2 : blobs) {
                        if (blob2 != blob) {
                            // The distance between the local blob and the other blob:
                            double dist = calculateBlobDistance(world, blob, blob2);

                            // 'part' is a number indicating how relevant this blob is for calculating
                            // the extraction cost. A big blob will have a big influence. If there is only
                            // one blob then this will be equal to 1.
                            double part = (double) blob2.size() / blocks.size();

                            // 'factor' indicates the cost of getting power out of blocks part of 'blob2'
                            // from the perspective of 'blob'.
                            double factor = 1 + (dist / DimensionalCellConfiguration.powerCellDistanceCap.get()) * (DimensionalCellConfiguration.powerCellCostFactor.get() - 1) * part;

                            totalfactor += factor;
                        }
                    }

                    // This is the average cost for getting power out of blocks from this blob:
                    totalfactor /= blobs.size();

                    // Set this to the coordinates of this blob
                    for (GlobalPos coordinate : blob) {
                        costFactor.put(coordinate, (float) totalfactor);
                    }
                }
            }
        }
        private void getBlob(Set<GlobalPos> todo, Set<GlobalPos> blob, GlobalPos coordinate) {
            blob.add(coordinate);
            for (Direction facing : OrientationTools.DIRECTION_VALUES) {
                GlobalPos offset = GlobalPos.of(coordinate.dimension(), coordinate.pos().relative(facing));
                if (todo.contains(offset)) {
                    todo.remove(offset);
                    getBlob(todo, blob, offset);
                }
            }
        }

        // Get all sets of cells that are connected to each other.
        private void getBlobs(List<Set<GlobalPos>> blobs) {
            Set<GlobalPos> todo = new HashSet<>(blocks);
            while (!todo.isEmpty()) {
                GlobalPos coordinate = todo.iterator().next();
                todo.remove(coordinate);
                Set<GlobalPos> blob = new HashSet<>();
                getBlob(todo, blob, coordinate);
                blobs.add(blob);
            }
        }

        public float calculateCostFactor(World world, GlobalPos g) {
            updateCostFactor(world);
            Float f = costFactor.get(g);
            return f == null ? 1.0f : f;
        }

        public int getEnergySingleBlock(DimensionalCellType type) {
            // Count how many blocks there would be if all powercells would be simple blocks:
            int simpleBlockCount = Math.max(1,
                    (blocks.size() - advancedBlocks - simpleBlocks) * DimensionalCellConfiguration.simpleFactor.get()
                    + advancedBlocks * DimensionalCellConfiguration.advancedFactor.get() * DimensionalCellConfiguration.simpleFactor.get()
                    + simpleBlocks);
            long rc = energy / simpleBlockCount;
            if (type.isAdvanced()) {
                rc *= DimensionalCellConfiguration.advancedFactor.get() * DimensionalCellConfiguration.simpleFactor.get();
            } else if (!type.isSimple()) {
                rc *= DimensionalCellConfiguration.simpleFactor.get();
            }
            return rc > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)rc;
        }

        public int extractEnergySingleBlock(DimensionalCellType type) {
            // Calculate the average energy with advanced, normal, and simple blocks seen as the equivalent number of simple blocks
            return extractEnergy(getEnergySingleBlock(type));
        }

        public int getEnergy() {
            return energy;
        }

        public int extractEnergy(int amount) {
            if(amount > energy) {
                amount = energy;
            }
            energy -= amount;
            return amount;
        }

        public int receiveEnergy(int amount) {
            if(amount > Integer.MAX_VALUE - energy) {
                amount = Integer.MAX_VALUE - energy;
            }
            energy += amount;
            return amount;
        }

        public void setEnergy(int energy) {
            this.energy = energy;
        }

        public void writeToNBT(CompoundNBT tagCompound){
            tagCompound.putInt("energy", energy);
            tagCompound.putInt("advanced", advancedBlocks);
            tagCompound.putInt("simple", simpleBlocks);
            ListNBT list = new ListNBT();
            for (GlobalPos block : blocks) {
                CompoundNBT tag = new CompoundNBT();
                tag.putString("dim", block.dimension().location().toString());
                tag.putInt("x", block.pos().getX());
                tag.putInt("y", block.pos().getY());
                tag.putInt("z", block.pos().getZ());
                list.add(tag);
            }

            tagCompound.put("blocks", list);
        }

        public void readFromNBT(CompoundNBT tagCompound){
            this.energy = tagCompound.getInt("energy");
            this.advancedBlocks = tagCompound.getInt("advanced");
            this.simpleBlocks = tagCompound.getInt("simple");
            blocks.clear();
            ListNBT list = tagCompound.getList("blocks", Constants.NBT.TAG_COMPOUND);
            for (int i = 0 ; i < list.size() ; i++) {
                CompoundNBT tag = list.getCompound(i);
                ResourceLocation id = new ResourceLocation(tag.getString("dim"));
                RegistryKey<World> type = LevelTools.getId(id);
                if (type == null) {
                    // Something went wrong!
                    Logging.logError("Unknown dimension '" + id.toString() + "'!");
                } else {
                    BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                    blocks.add(GlobalPos.of(type, pos));
                }
            }
        }
    }
}
