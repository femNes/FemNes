package org.femnes.femnes.core.extraChips;

// Not sure if there is a bettter place to put this

public class APU extends ExtraChip{

    public APU() {
        super(0b1111_1111_1111);
    }

    @Override
    protected int get(int relAddress, boolean internal) {
        return 0;
    }

    @Override
    protected void set(int relAddress, int value, boolean internal) {

    }
}
