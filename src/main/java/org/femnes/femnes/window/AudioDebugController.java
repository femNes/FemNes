package org.femnes.femnes.window;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.femnes.femnes.EmuHelper;
import org.femnes.femnes.core.AudioManager;
import org.femnes.femnes.core.DebugData;
import org.femnes.femnes.settings.AudioDebugMode;
import org.femnes.femnes.ui.charts.AudioBufferChart;
import org.femnes.femnes.ui.charts.DebugLineChart;

public class AudioDebugController extends WindowController {
    @FXML private VBox container;
    @FXML private ComboBox<AudioDebugMode> cbx_debugMode;
    @FXML private Slider slider_scale;

    private final ObjectProperty<AudioDebugMode> debugModeProperty = new SimpleObjectProperty<>(AudioDebugMode.Oscilloscope);
    private final AudioBufferChart audioOutputChart = new AudioBufferChart(DebugData.audioSeries);
    private final DebugLineChart audioBufferChart = new DebugLineChart(AudioManager.SAMPLER_BUFFER_SIZE, 256, 2);

    public AudioDebugController() {
        super("Audio Debug", "audio-debug");

        VBox.setVgrow(audioOutputChart, Priority.ALWAYS);
        container.getChildren().add(audioOutputChart);

        EmuHelper.setupComboBox(cbx_debugMode, debugModeProperty, AudioDebugMode.class, AudioDebugMode.stringConverter);
        debugModeProperty.addListener((obs, oldVal, newVal) -> {
            if (newVal != oldVal) {
                ObservableList<Node> children = container.getChildren();
                children.clear();
                switch (newVal) {
                    case Oscilloscope -> {
                        slider_scale.setVisible(true);
                        children.add(audioOutputChart);
                    }
                    case Buffer_Level -> {
                        slider_scale.setVisible(false);
                        children.add(audioBufferChart);
                    }
                }
            }
        });
        slider_scale.valueProperty().bindBidirectional(DebugData.audioScaleProperty);
    }

    @Override
    public void updateWindow() {
        audioBufferChart.add(AudioManager.READ_BUFFER_SIZE, DebugData.getAudioBufferFillLevel());
    }
}
