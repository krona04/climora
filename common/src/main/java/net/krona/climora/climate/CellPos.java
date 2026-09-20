package net.krona.climora.climate;

import static net.krona.climora.climate.SimulationConstants.CELL_SIZE_BLOCKS;

/**
 * Helpers for weather cell coordinates. A cell is packed into a long the same way as a chunk position.
 */
public final class CellPos {
    private CellPos() {
    }

    public static int blockToCell(int block) {
        return Math.floorDiv(block, CELL_SIZE_BLOCKS);
    }

    public static long key(int cellX, int cellZ) {
        return (cellX & 0xFFFFFFFFL) | ((cellZ & 0xFFFFFFFFL) << 32);
    }

    public static int x(long key) {
        return (int) key;
    }

    public static int z(long key) {
        return (int) (key >>> 32);
    }

    public static int minBlock(int cell) {
        return cell * CELL_SIZE_BLOCKS;
    }

    public static int centerBlock(int cell) {
        return cell * CELL_SIZE_BLOCKS + CELL_SIZE_BLOCKS / 2;
    }
}
