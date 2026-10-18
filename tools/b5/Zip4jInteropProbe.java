package io.github.lihuihnu.xuaneynote.data.b5;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

/**
 * B5.1 isolated test probe: shared by JVM/7-Zip and API26/35 instrumentation.
 * NOT production code, NOT a general-purpose ZIP extractor. Only synthetic inputs.
 */
public final class Zip4jInteropProbe {
    public static final String ENTRY = "payload.xuaney.zip";
    private static final int MAX_TEST_BYTES = 2 * 1024 * 1024;

    private Zip4jInteropProbe() {}

    private static void require(boolean condition, String why) {
        if (!condition) throw new IllegalArgumentException(why);
    }

    private static ZipParameters parameters(EncryptionMethod encryption, String name) {
        ZipParameters p = new ZipParameters();
        p.setFileNameInZip(name);
        if (encryption != null) {
            p.setEncryptFiles(true);
            p.setEncryptionMethod(encryption);
            if (encryption == EncryptionMethod.AES) {
                p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            }
        }
        return p;
    }

    private static void validPassword(char[] password) {
        require(password != null && password.length >= 12,
            "B5 candidate: empty or short synthetic password prohibited");
    }

    public static void writeAes(File input, File output, char[] password) throws Exception {
        validPassword(password);
        require(input.isFile() && !output.exists(), "Invalid input/overwrite");
        try (ZipFile zip = new ZipFile(output, password)) {
            zip.addFile(input, parameters(EncryptionMethod.AES, ENTRY));
        }
    }

    public static void writeAes128(File input, File output, char[] password) throws Exception {
        validPassword(password);
        ZipParameters params = parameters(EncryptionMethod.AES, ENTRY);
        params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_128);
        try (ZipFile zip = new ZipFile(output, password)) {
            zip.addFile(input, params);
        }
    }

    public static void writePlain(File input, File output) throws Exception {
        try (ZipFile zip = new ZipFile(output)) {
            zip.addFile(input, parameters(null, ENTRY));
        }
    }

    public static void writeLegacy(File input, File output, char[] password) throws Exception {
        validPassword(password);
        try (ZipFile zip = new ZipFile(output, password)) {
            zip.addFile(input, parameters(EncryptionMethod.ZIP_STANDARD, ENTRY));
        }
    }

    public static void writeTwoEntries(File input, File output, char[] password) throws Exception {
        validPassword(password);
        try (ZipFile zip = new ZipFile(output, password)) {
            zip.addFile(input, parameters(EncryptionMethod.AES, ENTRY));
            zip.addFile(input, parameters(EncryptionMethod.AES, "unexpected-second-entry.zip"));
        }
    }

    private static FileHeader oneAesHeader(ZipFile zip) throws Exception {
        List<FileHeader> headers = zip.getFileHeaders();
        require(!zip.isSplitArchive() && headers.size() == 1, "Must be exactly one non-split entry");
        FileHeader header = headers.get(0);
        require(ENTRY.equals(header.getFileName()) && !header.isDirectory(),
            "Unexpected entry or directory");
        require(header.isEncrypted() && header.getEncryptionMethod() == EncryptionMethod.AES
                && header.getAesExtraDataRecord() != null
                && header.getAesExtraDataRecord().getAesKeyStrength()
                    == AesKeyStrength.KEY_STRENGTH_256,
            "AES-256 is mandatory; plaintext/ZipCrypto/AES-128 rejected");
        require(header.getUncompressedSize() >= 1 &&
                header.getUncompressedSize() <= MAX_TEST_BYTES,
            "Unexpected test payload size");
        return header;
    }

    /**
     * Read until EOF; Zip4j validates AES authentication only at the end.
     * Do not confuse header/password-verifier success with complete MAC verification.
     */
    public static byte[] readAes(File archive, char[] password) throws Exception {
        validPassword(password);
        try (ZipFile zip = new ZipFile(archive, password)) {
            FileHeader header = oneAesHeader(zip);
            try (InputStream stream = zip.getInputStream(header);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] block = new byte[8192];
                int total = 0;
                int emptyReads = 0;
                for (;;) {
                    int length = stream.read(block);
                    if (length < 0) break;
                    if (length == 0) {
                        require(++emptyReads < 128, "No progress");
                        continue;
                    }
                    emptyReads = 0;
                    total += length;
                    require(total <= MAX_TEST_BYTES, "Test payload exceeds bound");
                    output.write(block, 0, length);
                }
                require(total == header.getUncompressedSize(), "Length mismatch");
                return output.toByteArray();
            }
        }
    }

    private static int ushort(byte[] raw, int offset) {
        return (raw[offset] & 255) | ((raw[offset + 1] & 255) << 8);
    }

    /** Alter the AE-2 authentication code, NOT the directory or compressed ciphertext. */
    public static void corruptMac(File archive, File output) throws Exception {
        require(!output.exists(), "Do not replace existing file");
        byte[] raw = Files.readAllBytes(archive.toPath());
        try (ZipFile zip = new ZipFile(archive)) {
            FileHeader header = oneAesHeader(zip);
            long local = header.getOffsetLocalHeader();
            require(local >= 0 && local < raw.length - 30, "Invalid local offset");
            int start = (int) local;
            require(raw[start] == 0x50 && raw[start + 1] == 0x4b
                && raw[start + 2] == 0x03 && raw[start + 3] == 0x04, "Invalid local ZIP header");
            long data = local + 30L + ushort(raw, start + 26) + ushort(raw, start + 28);
            long mac = data + header.getCompressedSize() - 10L; // AE-2 MAC is 10 bytes.
            require(mac >= data + 18 && mac + 10 <= raw.length, "MAC location is out of range");
            raw[(int) mac + 4] ^= 0x01;
            Files.write(output.toPath(), raw);
        }
    }

    public static void mustReject(File archive, char[] password) throws Exception {
        // A missing/corrupted test fixture must not masquerade as an authentication success.
        require(archive.isFile(), "Missing negative-test archive");
        try {
            readAes(archive, password);
        } catch (Exception expected) {
            return; // An actual parse/decrypt/read-to-EOF exception is required.
        }
        throw new AssertionError("Zip4j accepted an invalid or unauthenticated archive");
    }

    public static void main(String[] args) throws Exception {
        require(args.length >= 2, "Usage: command <files>; synthetic credentials via env");
        String rawPassword = System.getenv("XUANEY_B5_PROBE_PASSWORD");
        require(rawPassword != null, "Missing synthetic test password");
        char[] password = rawPassword.toCharArray();
        try {
            switch (args[0]) {
                case "write-aes":
                    require(args.length == 3, "write-aes input.zip output.zip");
                    writeAes(new File(args[1]), new File(args[2]), password);
                    break;
                case "write-aes128":
                    require(args.length == 3, "write-aes128 input.zip output.zip");
                    writeAes128(new File(args[1]), new File(args[2]), password);
                    break;
                case "write-plain":
                    require(args.length == 3, "write-plain input.zip output.zip");
                    writePlain(new File(args[1]), new File(args[2]));
                    break;
                case "write-legacy":
                    require(args.length == 3, "write-legacy input.zip output.zip");
                    writeLegacy(new File(args[1]), new File(args[2]), password);
                    break;
                case "write-two":
                    require(args.length == 3, "write-two input.zip output.zip");
                    writeTwoEntries(new File(args[1]), new File(args[2]), password);
                    break;
                case "verify":
                    require(args.length == 3, "verify archive.zip input.zip");
                    if (!MessageDigest.isEqual(readAes(new File(args[1]), password),
                            Files.readAllBytes(new File(args[2]).toPath()))) {
                        throw new AssertionError("Decrypted payload mismatch");
                    }
                    break;
                case "reject":
                    require(args.length == 2, "reject invalid.zip");
                    mustReject(new File(args[1]), password);
                    break;
                case "tamper-mac":
                    require(args.length == 3, "tamper-mac input.zip output.zip");
                    corruptMac(new File(args[1]), new File(args[2]));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown probe command");
            }
            System.out.println("PASS B5.1 " + args[0]);
        } finally {
            Arrays.fill(password, '\0'); // Synthetic passwords only; avoid further copies.
        }
    }
}
