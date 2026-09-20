package net.krona.climora.climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CellPosTest {
    private static final int SIZE = SimulationConstants.CELL_SIZE_BLOCKS;

    @Test
    void blocksMapToCellsIncludingNegativeCoordinates() {
        assertEquals(0, CellPos.blockToCell(0));
        assertEquals(0, CellPos.blockToCell(SIZE - 1));
        assertEquals(1, CellPos.blockToCell(SIZE));
        assertEquals(-1, CellPos.blockToCell(-1));
        assertEquals(-1, CellPos.blockToCell(-SIZE));
        assertEquals(-2, CellPos.blockToCell(-SIZE - 1));
    }

    @Test
    void keysRoundTrip() {
        int[][] cases = {{0, 0}, {5, -7}, {-1, -1}, {Integer.MAX_VALUE, Integer.MIN_VALUE}, {-234375, 234374}};
        for (int[] pos : cases) {
            long key = CellPos.key(pos[0], pos[1]);
            assertEquals(pos[0], CellPos.x(key));
            assertEquals(pos[1], CellPos.z(key));
        }
    }

    @Test
    void cellBoundsContainTheirBlocks() {
        for (int block : new int[]{-1000, -129, -1, 0, 64, 1000}) {
            int cell = CellPos.blockToCell(block);
            int min = CellPos.minBlock(cell);
            assertEquals(true, block >= min && block < min + SIZE);
            assertEquals(min + SIZE / 2, CellPos.centerBlock(cell));
        }
    }
}
