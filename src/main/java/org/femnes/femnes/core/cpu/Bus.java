package org.femnes.femnes.core.cpu;

public interface Bus {
    int  read(int address);
    void write(int address, int value);

    default boolean getIRQ() {
        return true;
    }
    default boolean getNMI() {
        return false;
    }
}
