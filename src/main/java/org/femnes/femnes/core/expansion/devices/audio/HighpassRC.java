package org.femnes.femnes.core.expansion.devices.audio;

import org.femnes.femnes.core.AudioManager;

public class HighpassRC implements AudioFilter {
    private final float alpha;
    private float prevInput = 0f;
    private float prevOutput = 0;

    public HighpassRC(float R, float C) {
        float dt = 1f / AudioManager.SAMPLE_RATE;
        alpha = (R * C) / (R * C + dt);
    }

    @Override
    public float process(float input) {
        float output = alpha * (prevOutput + input - prevInput);
        prevInput = input;
        prevOutput = output;
        return output;
    }
}
