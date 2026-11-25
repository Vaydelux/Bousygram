package com.vaydelux.bousygram.core.cast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Standalone synthetic-media test. No production APK, account or network access. */
public final class NativeCastRuntimeSmoke {
    private static final int IO_LIMIT = 32 * 1024;
    private static final long OUTPUT_LIMIT = 20L * 1024 * 1024;

    private enum Mode { GOOD, CANCEL, ZERO_READ, OVERSIZED_READ, REFUSE_WRITE, THROW_READ }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class Fixture implements NativeCastRemuxBridge.Callbacks, AutoCloseable {
        private final RandomAccessFile input;
        private final FileOutputStream output;
        private final Mode mode;
        private final long size;
        private long offset, written;
        private int reads, writes;
        private boolean revoked;

        Fixture(File source, File destination, Mode mode) throws IOException {
            input = new RandomAccessFile(source, "r");
            size = input.length();
            this.mode = mode;
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
                    count <= OUTPUT_LIMIT - written, "Invalid native write");
            writes++;
            if (mode == Mode.REFUSE_WRITE) return false;
            try {
                output.write(buffer, 0, count);
                written += count;
                if (mode == Mode.CANCEL) revoked = true;
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

    private static void test(File root, String extension, int kind, Mode mode) throws IOException {
        File source = new File(root, "source." + extension);
        File output = new File(root, mode == Mode.GOOD ? "converted." + extension + ".mp4" : "negative.tmp");
        require(source.isFile() && source.length() > 0, "Missing synthetic source");
        try (Fixture fixture = new Fixture(source, output, mode)) {
            int result;
            if (mode == Mode.THROW_READ) {
                boolean caught = false;
                try { NativeCastRemuxBridge.run(kind, fixture.size, fixture); }
                catch (IllegalStateException e) { caught = "synthetic callback fault".equals(e.getMessage()); }
                require(caught && fixture.writes == 0, "JNI callback exception was not propagated");
                System.out.println("PASS " + extension + " " + mode);
                return;
            }
            result = NativeCastRemuxBridge.run(kind, fixture.size, fixture);
            if (mode == Mode.GOOD) {
                require(result == 0, extension + " native status " + result);
                require(fixture.offset == fixture.size && fixture.reads > 1 &&
                        fixture.writes > 1 && fixture.written > 0, "Incomplete callback conversion");
            } else {
                require(result != 0, "Negative case returned success: " + mode);
                if (mode == Mode.CANCEL) require(fixture.writes == 1 && result == 5, "Cancellation not enforced");
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
        require(args.length == 1, "Expected private test directory");
        File root = new File(args[0]);
        System.load(new File(root, "libbousygram_cast_remux.so").getAbsolutePath());
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
