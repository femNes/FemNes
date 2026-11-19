package org.femnes.femnes.core.extraChips;

// Not sure if there is a bettter place to put this

public class PPU extends ExtraChip{

    public PPU() {
        super(0b0000_0000_0111);
    }

    @Override
    protected int get(int relAddress, boolean internal) {
        return 0;
    }

    @Override
    protected void set(int relAddress, int value, boolean internal) {

    }
}
