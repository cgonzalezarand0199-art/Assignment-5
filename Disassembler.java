import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Disassembler {
    private static final String[] REGISTERS = {
            "$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3",
            "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7",
            "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
            "$t8", "$t9", "$k0", "$k1", "$gp", "$sp", "$fp", "$ra"
    };

    static class Image {
        int textBase;
        int dataBase;
        byte[] text;
        byte[] data;
        List<Integer> words = new ArrayList<>();
    }

    public static void main(String[] args) {
        try {
            if (args.length > 1) {
                System.out.println("Usage: java Disassembler [fizzbuzz.mhc-bin]");
                return;
            }

            Path input;
            if (args.length == 1) {
                input = Paths.get(args[0]);
            } else {
                Path currentFolder = Paths.get("fizzbuzz.mhc-bin");
                Path srcFolder = Paths.get("src", "fizzbuzz.mhc-bin");
                input = Files.exists(currentFolder) ? currentFolder : srcFolder;
            }
            Image image = readImage(input);
            String outputName = outputName(input);
            String assembly = buildAssembly(image);
            Files.write(Paths.get(outputName),
                    assembly.getBytes(StandardCharsets.UTF_8));

            System.out.println("Created " + outputName);
            System.out.println("Instructions: " + image.words.size());
            System.out.println("Text bytes: " + image.text.length);
            System.out.println("Data bytes: " + image.data.length);
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Disassembly failed: " + e.getMessage());
        }
    }

    private static Image readImage(Path input) throws IOException {
        try (InputStream raw = Files.newInputStream(input)) {
            raw.mark(4);
            byte[] magic = new byte[4];
            int count = raw.read(magic);
            raw.close();

            if (count == 4 && new String(magic, StandardCharsets.US_ASCII)
                    .equals("MHC1")) {
                return readBinary(input);
            }
        }

        return readAscii(input);
    }

    private static Image readBinary(Path input) throws IOException {
        Image image = new Image();

        try (DataInputStream in = new DataInputStream(Files.newInputStream(input))) {
            byte[] magic = new byte[4];
            in.readFully(magic);

            if (!new String(magic, StandardCharsets.US_ASCII).equals("MHC1")) {
                throw new IOException("Invalid MHC binary header.");
            }

            image.textBase = in.readInt();
            image.dataBase = in.readInt();
            int textLength = in.readInt();
            int dataLength = in.readInt();

            if (textLength < 0 || dataLength < 0 || textLength % 4 != 0) {
                throw new IOException("Invalid text or data length.");
            }

            image.text = new byte[textLength];
            image.data = new byte[dataLength];
            in.readFully(image.text);
            in.readFully(image.data);
        }

        addWords(image);
        return image;
    }

    private static Image readAscii(Path input) throws IOException {
        Image image = new Image();
        boolean inText = false;
        boolean inData = false;
        List<Byte> dataBytes = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                input, StandardCharsets.UTF_8)) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("TEXT_BASE=")) {
                    image.textBase = parseHex(line.substring(10));
                } else if (line.startsWith("DATA_BASE=")) {
                    image.dataBase = parseHex(line.substring(10));
                } else if (line.equals("[TEXT INSTRUCTIONS]")) {
                    inText = true;
                    inData = false;
                } else if (line.equals("[DATA]")) {
                    inText = false;
                    inData = true;
                } else if (inText && !line.isEmpty()
                        && Character.digit(line.charAt(0), 16) >= 0) {
                    String[] fields = line.split("\\s+");
                    if (fields.length >= 2) {
                        image.words.add((int) Long.parseLong(fields[1], 16));
                    }
                } else if (inData && !line.isEmpty()
                        && Character.digit(line.charAt(0), 16) >= 0) {
                    String[] fields = line.split("\\s+");
                    if (fields.length >= 2) {
                        dataBytes.add((byte) Integer.parseInt(fields[1]));
                    }
                }
            }
        }

        image.text = new byte[image.words.size() * 4];
        for (int i = 0; i < image.words.size(); i++) {
            int word = image.words.get(i);
            for (int shift = 24; shift >= 0; shift -= 8) {
                image.text[i * 4 + (24 - shift) / 8] =
                        (byte) (word >>> shift);
            }
        }

        image.data = new byte[dataBytes.size()];
        for (int i = 0; i < dataBytes.size(); i++) {
            image.data[i] = dataBytes.get(i);
        }

        return image;
    }

    private static void addWords(Image image) {
        for (int i = 0; i < image.text.length; i += 4) {
            int word = ((image.text[i] & 255) << 24)
                    | ((image.text[i + 1] & 255) << 16)
                    | ((image.text[i + 2] & 255) << 8)
                    | (image.text[i + 3] & 255);
            image.words.add(word);
        }
    }

    private static String buildAssembly(Image image) {
        StringBuilder output = new StringBuilder();
        Map<Integer, String> labels = findLabels(image);

        output.append("# Reconstructed by Disassembler.java\n");
        output.append("# Text base: 0x").append(hex(image.textBase)).append('\n');
        output.append("# Data base: 0x").append(hex(image.dataBase)).append('\n');
        output.append("# Byte order: BIG_ENDIAN\n\n");
        output.append(".data\n");
        writeData(output, image.data);
        output.append("\n.text\n");
        output.append(".globl main\n");

        for (int i = 0; i < image.words.size(); i++) {
            int address = image.textBase + i * 4;
            String label = labels.get(address);

            if (label != null) {
                output.append(label).append(":\n");
            }

            output.append("    ")
                    .append(decode(image.words.get(i), address, labels))
                    .append('\n');
        }

        return output.toString();
    }

    private static Map<Integer, String> findLabels(Image image) {
        Map<Integer, String> labels = new HashMap<>();
        Set<Integer> targets = new HashSet<>();
        int textEnd = image.textBase + image.text.length;

        for (int i = 0; i < image.words.size(); i++) {
            int word = image.words.get(i);
            int address = image.textBase + i * 4;
            int opcode = word >>> 26;
            int target = -1;

            if (opcode == 2 || opcode == 3) {
                target = ((address + 4) & 0xF0000000)
                        | ((word & 0x03FFFFFF) << 2);
            } else if (opcode == 4 || opcode == 5) {
                int offset = (short) (word & 0xFFFF);
                target = address + 4 + offset * 4;
            }

            if (target >= image.textBase && target < textEnd
                    && (target - image.textBase) % 4 == 0) {
                targets.add(target);
            }
        }

        labels.put(image.textBase, "main");
        int next = 1;

        for (int address : targets) {
            if (address == image.textBase) continue;
            labels.put(address, "L" + next++);
        }

        return labels;
    }

    private static String decode(int word, int address,
                                 Map<Integer, String> labels) {
        int opcode = word >>> 26;
        int rs = (word >>> 21) & 31;
        int rt = (word >>> 16) & 31;
        int rd = (word >>> 11) & 31;
        int shamt = (word >>> 6) & 31;
        int funct = word & 63;
        int immediate = word & 0xFFFF;

        switch (opcode) {
            case 0:
                if (word == 0x0000000C) return "syscall";
                if (funct == 0x21) return "addu " + r(rd) + ", " + r(rs) + ", " + r(rt);
                if (funct == 0x1A) return "div " + r(rs) + ", " + r(rt);
                if (funct == 0x10) return "mfhi " + r(rd);
                if (funct == 8) return "jr " + r(rs);
                if (funct == 0) return "sll " + r(rd) + ", " + r(rt) + ", " + shamt;
                break;
            case 2:
                return "j " + labelFor(jumpTarget(word, address), labels);
            case 3:
                return "jal " + labelFor(jumpTarget(word, address), labels);
            case 4:
                return "beq " + r(rs) + ", " + r(rt) + ", "
                        + labelFor(branchTarget(word, address), labels);
            case 5:
                return "bne " + r(rs) + ", " + r(rt) + ", "
                        + labelFor(branchTarget(word, address), labels);
            case 8:
                return "addi " + r(rt) + ", " + r(rs) + ", " + signed(immediate);
            case 9:
                return "addiu " + r(rt) + ", " + r(rs) + ", " + signed(immediate);
            case 13:
                return "ori " + r(rt) + ", " + r(rs) + ", " + immediate;
            case 15:
                return "lui " + r(rt) + ", " + immediate;
            case 35:
                return "lw " + r(rt) + ", " + signed(immediate) + "(" + r(rs) + ")";
            case 43:
                return "sw " + r(rt) + ", " + signed(immediate) + "(" + r(rs) + ")";
            default:
                break;
        }

        return ".word 0x" + hex(word);
    }

    private static void writeData(StringBuilder output, byte[] data) {
        int i = 0;

        while (i < data.length) {
            int start = i;

            while (i < data.length && data[i] != 0) i++;

            if (i < data.length && i > start && printable(data, start, i)) {
                output.append("    .asciiz \"")
                        .append(escape(data, start, i))
                        .append("\"\n");
                i++;
            } else {
                output.append("    .byte ")
                        .append(data[start] & 255).append('\n');
                i = start + 1;
            }
        }
    }

    private static boolean printable(byte[] data, int start, int end) {
        for (int i = start; i < end; i++) {
            int value = data[i] & 255;
            if (value < 32 || value > 126 || value == '"' || value == '\\') {
                return false;
            }
        }
        return true;
    }

    private static String escape(byte[] data, int start, int end) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < end; i++) {
            char c = (char) (data[i] & 255);
            if (c == '"' || c == '\\') result.append('\\');
            result.append(c);
        }
        return result.toString();
    }

    private static int branchTarget(int word, int address) {
        return address + 4 + (short) (word & 0xFFFF) * 4;
    }

    private static int jumpTarget(int word, int address) {
        return ((address + 4) & 0xF0000000)
                | ((word & 0x03FFFFFF) << 2);
    }

    private static String labelFor(int address, Map<Integer, String> labels) {
        String label = labels.get(address);
        return label == null ? "0x" + hex(address) : label;
    }

    private static String r(int register) {
        return REGISTERS[register];
    }

    private static int signed(int value) {
        return (short) value;
    }

    private static int parseHex(String value) {
        return (int) Long.parseLong(value.trim().replace("0x", ""), 16);
    }

    private static String hex(int value) {
        return String.format("%08X", value);
    }

    private static String outputName(Path input) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return input.toAbsolutePath().getParent()
                .resolve(name + ".disassembled.asm").toString();
    }
}
