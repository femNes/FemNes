package org.femnes.femnes.core;

import javafx.stage.Stage;
import org.femnes.femnes.core.cpu.Bus;
import org.femnes.femnes.core.cpu.CPU;
import org.femnes.femnes.core.expansion.ExpansionData;
import org.femnes.femnes.core.expansion.ExpansionManager;
import org.femnes.femnes.core.extraChips.*;
import org.femnes.femnes.settings.ExpansionPort;
import org.femnes.femnes.window.MainWindowController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

import static org.femnes.femnes.core.ConfigManager.updatePeriod;
import static org.femnes.femnes.core.ConfigManager.clockPeriod;

public abstract class MainManager {
    private static final int HEADER_SIZE = 16;
    private static final char[] ID_CHARS = {'c', '6', '5', 0x15};
    private static final int MIN_FILE_SIZE = HEADER_SIZE; // 16 B
    private static final int MAX_FILE_SIZE = 0x4_0000;    // 256 KiB

    private static final Bus bus = new Bus() {
        @Override
        public int read(int address) {
            return MainManager.get(address, true);
        }
        @Override
        public void write(int address, int value) {
            MainManager.set(address, value, true);
        }

        @Override
        public boolean getIRQ() {
            return MainManager.getIRQ();
        }
        @Override
        public boolean getNMI() {
            return MainManager.getNMI();
        }
    };

    private static final int ROM_SIZE = 0x8000;
    private static final byte[] rom = new byte[ROM_SIZE];
    private static final byte[] ram = new byte[0x400];

    private static final ACIA acia = new ACIA();
    private static final VIA  via  = new VIA();
    private static final LCD  lcd  = new LCD();
    private static final CPU  cpu  = new CPU(bus);

    private static final PPU ppu  = new PPU();
    private static final APU apu  = new APU();


    public static MainWindowController mainWindowController;

    private static final long MAX_LAG_BUFFER = 50_000_000L;  // 50 ms
    private static long timeSinceLastUpdate = System.nanoTime();

    public static volatile boolean running = true;
    public static volatile boolean paused  = true;
    public static volatile boolean step    = false;
    public static volatile boolean reset   = true;


    public static boolean enable_cat65 = true;


    private static final int INES_HEADER_SIZE = 16;
    private static final int PRG_BANK_SIZE = 16 * 1024; // 16 KiB
    private static final int CHR_BANK_SIZE = 8 * 1024;  // 8 KiB
    private static final int TRAINER_SIZE = 512;





    public static CPU getCpu() {
        return cpu;
    }
    public static LCD getLcd() {
        return lcd;
    }

    public static void stop() {
        running = false;
        System.exit(0);
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    public static void startEmulatorThread() {
        Thread updateThread = new Thread(() -> {
            long lastTime = System.nanoTime();
            long updateTimeAccumulator = 0L;
            long clockTimeAccumulator = 0L;
            long currentTime;
            long sleepTime;

            while (running) {
                currentTime = System.nanoTime();
                timeSinceLastUpdate = currentTime - lastTime;
                lastTime = currentTime;

                updateTimeAccumulator = Math.min(updateTimeAccumulator + timeSinceLastUpdate, MAX_LAG_BUFFER);

                while (updateTimeAccumulator >= updatePeriod) {
                    if (!paused || step) {
                        clockTimeAccumulator += ConfigManager.DEFAULT_UPDATE_PERIOD;
                        if (reset) {
                            reset = false;
                            via.reset();
                            acia.reset();
                            ExpansionManager.reset();
                            cpu.reset();
                            step = false;
                        } else {
                            via.update();
                            acia.update();
                            ExpansionManager.update();
                            if (clockTimeAccumulator >= clockPeriod) {
                                via.clock();
                                acia.clock();
                                ExpansionManager.clock();
                                cpu.clock();
                                if (cpu.isAtInstructionBoundary()) step = false;

                                DebugData.executedClocks++;
                                clockTimeAccumulator -= clockPeriod;
                            }
                        }
                    }
                    AudioManager.updateAudio();
                    updateTimeAccumulator -= updatePeriod;
                }

                sleepTime = updatePeriod - updateTimeAccumulator;
                if (sleepTime > 0L) {
                    LockSupport.parkNanos(sleepTime);
                }
            }
        }, "Cat65 Update");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    public static void startUi(Stage stage) {
        mainWindowController = new MainWindowController(stage);
    }

    public static int getPA(int pinState) {
        // ToDo: get PortA
        return 0x00;
    }
    public static void setPA(int pinState) {
        // ToDo: set PortA
    }
    /**
     * bits 0-3: Data
     **/
    public static int getPB(int pinState) {
        return lcd.get(pinState);
    }
    /**
     * bits 0-3: Data<br>
     * bit   4:  RS<br>
     * bit   5:  R/W<br>
     * bit   6:  E
     **/
    public static void setPB(int pinState) {
        lcd.set(pinState);
    }

    public static boolean getIRQ() {
        return via.getIRQ() && acia.getIRQ() && ExpansionManager.getIRQ();
    }
    public static boolean getNMI() {
        return ExpansionManager.getNMI();
    }

    public static int get(int address, boolean internal) {


        if (enable_cat65 == false){
            address &= 0xffff;
            return 0xff & (switch (address >> 12) {
                case 0x0,0x1     -> ram[address & 0x07ff];
                case 0x2,0x3     -> ppu.read(address - 0x2000, internal);
                case 0x4         -> apu.read(address - 0x4000, internal);
                case 0x5,0x6,0x7 -> 0x00;  // ToDo: replace with other stuff
                default          -> rom[address - 0x8000];
            });
        } else {

            // Cat65 Leftover code
            // DO NOT REMOVE
            address &= 0xffff;
            return 0xff & (switch (address >> 12) {
                case 0x0, 0x1, 0x2, 0x3 -> ram[address];
                case 0x4, 0x7 -> ExpansionManager.get(address, internal);
                case 0x5 -> acia.read(address - 0x5000, internal);
                case 0x6 -> via.read(address - 0x6000, internal);
                default -> rom[address - 0x8000];
            });
        }
    }
    public static void set(int address, int value, boolean internal) {

        if (enable_cat65 == false){
            address &= 0xffff;
            value &= 0xff;
            switch (address >> 12) {
                case 0x0,0x1         -> ram[address & 0x07ff] = (byte) value;
                case 0x2,0x3         -> ppu.write(address - 0x2000, value, internal);
                case 0x4             -> apu.write(address - 0x4000, value, internal);
                case 0x8,0x9,0xa,0xb,
                    0xc,0xd,0xe,0xf -> {
                    if (!internal) rom[address - 0x8000] = (byte) value;
                }
            }
        }else {
            address &= 0xffff;
            value &= 0xff;
            switch (address >> 12) {
                case 0x0, 0x1, 0x2, 0x3 -> ram[address] = (byte) value;
                case 0x4, 0x7 -> ExpansionManager.set(address, value, internal);
                case 0x5 -> acia.write(address - 0x5000, value, internal);
                case 0x6 -> via.write(address - 0x6000, value, internal);
                default -> {
                    if (!internal) rom[address - 0x8000] = (byte) value;
                }
            }
        }
    }

    @SuppressWarnings("CallToPrintStackTrace")
    public static void loadFile(File file) {
        boolean p = paused;
        paused = true;

        Arrays.fill(rom, (byte) 0x00);
        ExpansionData.resetData();

        try {
            System.out.println();

            Path path = file.toPath();
            long size = Files.size(path);
            if (size < MIN_FILE_SIZE) {
                throw new IOException("[!] Selected file is smaller than the minimum file size of %d bytes!\n".formatted(MIN_FILE_SIZE));
            } else if (size > MAX_FILE_SIZE) {
                throw new IOException("[!] Selected file is larger than the maximum file size of %d bytes!\n".formatted(MAX_FILE_SIZE));
            }
            byte[] data = Files.readAllBytes(path);

            boolean c65 = true;
            for (int i = 0; i < 4; i++) {
                if (data[i] != ID_CHARS[i]) {
                    c65 = false;
                    break;
                }
            }
            if (c65) {
                loadC65(data);
            } else {
                loadRaw(data);
            }
        } catch (Exception e) {
            System.err.println("[!] Could not open file...");
            e.printStackTrace();
        } finally {
            reset = true;
            paused = p;
        }
    }
    private static void loadRaw(byte[] file) {
        System.out.println("[#] loading ROM");
        if (file.length < ROM_SIZE) {
            System.err.println("[!] Warning! this ROM has a file size below 32KiB!");
        } else if (file.length > ROM_SIZE) {
            System.err.printf("[!] Warning! This ROM has %d unused bytes left over\n", file.length - ROM_SIZE);
        }

        for (int i = 0; i < rom.length; i++) {
            rom[i] = (i < file.length) ? file[i] : 0x00;
        }
    }
    private static void loadC65(byte[] file) {
        System.out.println("[#] loading .c65 file");
        // 0x7 to 0xf are reserved for future use

        System.out.println("[*] loading Expansion...");
        ExpansionManager.ex04Property.setValue(ExpansionPort.fromID(file[0x4]));
        ExpansionManager.ex07Property.setValue(ExpansionPort.fromID(file[0x5]));

        System.out.println("[*] loading ROM...");
        if (file.length < (ROM_SIZE + HEADER_SIZE)) {
            System.err.println("[!] Warning! this .c65 file has a ROM size below 32KiB!");
        }

        int index = HEADER_SIZE;
        for (int a = 0; a < rom.length; a++) {
            rom[a] = (index < file.length) ? file[index] : 0x00;
            index++;
        }
        index = ExpansionData.loadC65(file, index);

        if (index < file.length) {
            System.err.printf("[!] Warning! This file has %d unused bytes left over\n", file.length - index);
        }
        reset = true;
    }




    private static void loadINes(byte[] file) {
        System.out.println("[#] loading .nes (iNES) file");

        if (file == null || file.length < INES_HEADER_SIZE) {
            System.err.println("[!] Error: file is too small to contain a valid iNES header!");
            return;
        }

        // 1. Verify magic header: 'N', 'E', 'S', 0x1A
        if (file[0x00] != 'N' || file[0x01] != 'E' || file[0x02] != 'S' || file[0x03] != 0x1A) {
            System.err.println("[!] Error: invalid iNES magic header!");
            return;
        }

        int prgBanks = Byte.toUnsignedInt(file[0x04]);
        int chrBanks = Byte.toUnsignedInt(file[0x05]);
        int flags6 = Byte.toUnsignedInt(file[0x06]);
        int flags7 = Byte.toUnsignedInt(file[0x07]);

        boolean verticalMirroring = (flags6 & 0x01) != 0;
        boolean hasBatteryRam = (flags6 & 0x02) != 0;
        boolean hasTrainer = (flags6 & 0x04) != 0;
        boolean fourScreenVram = (flags6 & 0x08) != 0;

        // Guard against old "DiskDude!" / junk headers in bytes 7..15 when not NES 2.0
        boolean isNes2 = (flags7 & 0x0C) == 0x08;
        boolean isStandardINes = (flags7 & 0x0C) == 0x00
                && file[12] == 0 && file[13] == 0 && file[14] == 0 && file[15] == 0;

        int mapperLow = (flags6 >>> 4) & 0x0F;
        int mapperHigh = (isNes2 || isStandardINes) ? ((flags7 >>> 4) & 0x0F) : 0;
        int mapperId = (mapperHigh << 4) | mapperLow;

        System.out.printf("[*] Mapper: %d | PRG: %d x 16KiB | CHR: %d x 8KiB | Trainer: %s%n",
                mapperId, prgBanks, chrBanks, hasTrainer ? "yes" : "no");

        // Configure mirroring / cart hardware flags if your emulator tracks them
        // MirroringMode.set(fourScreenVram ? FOUR_SCREEN : (verticalMirroring ? VERTICAL : HORIZONTAL));
        // BatteryRam.setEnabled(hasBatteryRam);

        int index = INES_HEADER_SIZE;

        // 2. Skip optional 512-byte trainer
        if (hasTrainer) {
            if (index + TRAINER_SIZE > file.length) {
                System.err.println("[!] Warning! Truncated trainer data in .nes file!");
            }
            index += TRAINER_SIZE;
        }

        // 3. Load PRG-ROM
        int prgSize = prgBanks * PRG_BANK_SIZE;
        byte[] prgRom = new byte[prgSize];
        for (int i = 0; i < prgSize; i++) {
            prgRom[i] = (index < file.length) ? file[index] : 0x00;
            index++;
        }
        if (index - (hasTrainer ? INES_HEADER_SIZE + TRAINER_SIZE : INES_HEADER_SIZE) < prgSize) {
            System.err.println("[!] Warning! .nes file ended early while reading PRG-ROM!");
        }

        // 4. Load CHR-ROM (or allocate CHR-RAM if chrBanks == 0)
        int chrSize = (chrBanks > 0) ? (chrBanks * CHR_BANK_SIZE) : CHR_BANK_SIZE;
        byte[] chrRom = new byte[chrSize];
        if (chrBanks > 0) {
            for (int i = 0; i < chrSize; i++) {
                chrRom[i] = (index < file.length) ? file[index] : 0x00;
                index++;
            }
        }

        // Assign to your emulator fields (e.g., this.prgRom = prgRom; this.chrRom = chrRom; this.mapper = mapperId;)
        if (index < file.length) {
            System.err.printf("[!] Warning! This file has %d unused bytes left over%n", file.length - index);
        }

        reset = true;
    }
}
