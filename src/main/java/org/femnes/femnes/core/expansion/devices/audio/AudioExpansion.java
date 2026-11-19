package org.femnes.femnes.core.expansion.devices.audio;

import org.femnes.femnes.core.expansion.devices.ExpansionDevice;

public abstract class AudioExpansion extends ExpansionDevice {
    public AudioExpansion(int mirrorAddressMask) {
        super(mirrorAddressMask);
    }

    public abstract float getAudioSample();

    protected static float digitalToAnalog4Bit(int bits) {
        return (bits / 15f);
    }
}
