package com.vaydelux.bousygram.core.cast;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Standalone synthetic-media test. No production APK, account or network access. */
public final class NativeCastRuntimeSmoke {
    private static final int IO_LIMIT = 32 * 1024;
    private static final long OUTPUT_LIMIT = 20L * 1024 * 1024;
    private static final long EXT_OUTPUT_LIMIT = 64L * 1024 * 1024;
    private static native int runExtended(int kind, long size, NativeCastRemuxBridge.Callbacks callbacks);

    private enum Mode { GOOD, CANCEL, ZERO_READ, OVERSIZED_READ, REFUSE_WRITE, THROW_READ, CANCEL_LATE }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class Fixture implements NativeCastRemuxBridge.Callbacks, AutoCloseable {
        private final RandomAccessFile input;
        private final FileOutputStream output;
        private final Mode mode;
        private final long size, outputLimit;
        private long offset, written;
        private int reads, writes;
        private boolean revoked;

        Fixture(File source, File destination, Mode mode, boolean extended) throws IOException {
            input = new RandomAccessFile(source, "r");
            size = input.length();
            this.mode = mode;
            outputLimit = extended ? EXT_OUTPUT_LIMIT : OUTPUT_LIMIT;
            try {
                output = new FileOutputStream(destination);
            } catch (IOException e) {
                input.close();
                throw e;
            }
        }

        @Override public int read(long position, byte[] buffer, int count) {
            require(!revoked && position == offset && count > 0 && count <= IO_LIMIT &&
                    count <= buffer.length && count <= size - offset, "Invalid native read");
            reads++;
            if (mode == Mode.ZERO_READ) return 0;
            if (mode == Mode.OVERSIZED_READ) return count + 1;
            if (mode == Mode.THROW_READ) throw new IllegalStateException("synthetic callback fault");
            try {
                // Force short reads, including irregular boundaries across containers.
                int n = input.read(buffer, 0, Math.min(count, 4093));
                require(n > 0, "Unexpected source EOF");
                offset += n;
                return n;
            } catch (IOException e) {
                throw new IllegalStateException("fixture read failed", e);
            }
        }

        @Override public boolean write(byte[] buffer, int count) {
            require(!revoked && count > 0 && count <= IO_LIMIT && count <= buffer.length &&
                    count <= outputLimit - written, "Invalid native write");
            writes++;
            if (mode == Mode.REFUSE_WRITE) return false;
            try {
                output.write(buffer, 0, count);
                written += count;
                if (mode == Mode.CANCEL || (mode == Mode.CANCEL_LATE && written >= 2L * 1024 * 1024)) revoked = true;
                return true;
            } catch (IOException e) {
                throw new IllegalStateException("fixture write failed", e);
            }
        }

        @Override public boolean active() { return !revoked; }
        @Override public void close() throws IOException {
            try { output.close(); } finally { input.close(); }
        }
    }

    private static long memoryHighWaterKiB() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmHWM:")) {
                    String[] parts = line.trim().split("\\s+");
                    require(parts.length == 3 && "kB".equals(parts[2]), "Unexpected memory metric");
                    return Long.parseLong(parts[1]);
                }
            }
        }
        throw new IOException("Missing process memory high-water");
    }

    private static void test(File root, String extension, int kind, Mode mode) throws IOException {
        test(root, extension, kind, mode, false);
    }

    private static void test(File root, String extension, int kind, Mode mode, boolean extended) throws IOException {
        System.out.println("START " + extension + " " + mode);
        System.out.flush();
        File source = new File(root, "source." + extension);
        File output = new File(root, mode == Mode.GOOD ? "converted." + extension + ".mp4" : "negative.tmp");
        require(source.isFile() && source.length() > 0, "Missing synthetic source");
        if (extended) require(source.length() > OUTPUT_LIMIT && source.length() <= EXT_OUTPUT_LIMIT,
                "Extended fixture must exceed finite limits");
        long baseline = extended ? memoryHighWaterKiB() : 0;
        try (Fixture fixture = new Fixture(source, output, mode, extended)) {
            int result;
            if (mode == Mode.THROW_READ) {
                boolean caught = false;
                try { NativeCastRemuxBridge.run(kind, fixture.size, fixture); }
                catch (IllegalStateException e) { caught = "synthetic callback fault".equals(e.getMessage()); }
                require(caught && fixture.writes == 0, "JNI callback exception was not propagated");
                System.out.println("PASS " + extension + " " + mode);
                return;
            }
            if (extended) {
                require(NativeCastRemuxBridge.run(kind, fixture.size, fixture) == 1 &&
                        fixture.reads == 0 && fixture.writes == 0, "Finite profile accepted extended fixture");
                result = runExtended(kind, fixture.size, fixture);
                long high = memoryHighWaterKiB();
                System.out.println("EXT_MEMORY baseline_hwm_kib=" + baseline + " peak_hwm_kib=" + high +
                        " growth_kib=" + (high - baseline));
                // Qualification ceiling for process high-water growth only,
                // not a hard allocator cap or physical device measurement.
                require(high >= baseline && high - baseline <= 128L * 1024, "Extended memory growth exceeded gate");
            } else result = NativeCastRemuxBridge.run(kind, fixture.size, fixture);
            if (mode == Mode.GOOD) {
                require(result == 0, extension + " native status " + result);
                require(fixture.offset == fixture.size && fixture.reads > 1 &&
                        fixture.writes > 1 && fixture.written > 0, "Incomplete callback conversion");
                if (extended) require(fixture.written > OUTPUT_LIMIT, "Extended output did not exceed finite cap");
            } else {
                require(result != 0, "Negative case returned success: " + mode);
                if (mode == Mode.CANCEL) require(fixture.writes == 1 && result == 5, "Cancellation not enforced");
                if (mode == Mode.CANCEL_LATE) require(extended && result == 5 && fixture.written >= 2L * 1024 * 1024 &&
                        fixture.writes > 1, "Late cancellation not enforced");
                if (mode == Mode.ZERO_READ || mode == Mode.OVERSIZED_READ)
                    require(fixture.writes == 0 && result == 2, "Invalid read not rejected");
                if (mode == Mode.REFUSE_WRITE) require(fixture.writes == 1 && result == 2, "Write refusal not enforced");
            }
            System.out.println("PASS " + extension + " " + mode + " status=" + result +
                    " reads=" + fixture.reads + " writes=" + fixture.writes + " bytes=" + fixture.written);
        } finally {
            if (mode != Mode.GOOD && output.exists()) require(output.delete(), "Negative output cleanup failed");
        }
    }

    public static void main(String[] args) throws IOException {
        require(args.length == 1 || args.length == 3 || args.length == 4, "Expected private test directory [format mode [EXTENDED]]");
        File root = new File(args[0]);
        System.load(new File(root, "libbousygram_cast_remux.so").getAbsolutePath());
        if (args.length >= 3) {
            String extension = args[1];
            require("mkv".equals(extension) || "ts".equals(extension), "Unknown fixture");
            Mode mode = Mode.valueOf(args[2]);
            System.out.println("BEGIN " + extension + " " + mode);
            System.out.flush();
            boolean extended = args.length == 4;
            require(!extended || ("EXTENDED".equals(args[3]) && (mode == Mode.GOOD || mode == Mode.CANCEL_LATE)),
                    "Invalid extended qualification case");
            test(root, extension, "mkv".equals(extension) ? 1 : 2, mode, extended);
            System.out.println(extended ? "NATIVE_EXTENDED_CASE_PASS" : "NATIVE_SINGLE_CASE_PASS");
            return;
        }
        require(NativeCastRemuxBridge.run(0, 1, null) == 1, "Invalid kind accepted");
        require(NativeCastRemuxBridge.run(1, 16L * 1024 * 1024 + 1, null) == 1, "Oversize input accepted");
        for (Mode mode : new Mode[]{Mode.ZERO_READ, Mode.OVERSIZED_READ, Mode.THROW_READ, Mode.REFUSE_WRITE, Mode.CANCEL}) {
            test(root, "mkv", 1, mode);
            test(root, "ts", 2, mode);
        }
        // A positive conversion after failures also exercises native cleanup/re-entry.
        test(root, "mkv", 1, Mode.GOOD);
        test(root, "ts", 2, Mode.GOOD);
        System.out.println("NATIVE_RUNTIME_CALLBACKS_PASS");
    }
}
