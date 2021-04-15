package de.stuefe.repros.metaspace.internals;

import java.util.Random;

public class Utils {

    static public Random rand = new Random();

    static final float defaultWiggle = 0.0f;

    static public void createRandomClass(String classname, int sizeFactor) {
        createRandomClass(classname, sizeFactor, defaultWiggle);
    }

    static public String nameClass(int number) {
        return "myclass_" + number;
    }

    // n -> n + (n * [-wiggle...wiggle))
    static private int fluctuate(int n, float wiggle) {
        int max_abs_deviation = (int)((float)n * wiggle);
        int rand_deviation = 0;
        if (max_abs_deviation >= 1) {
            rand_deviation = rand.nextInt((max_abs_deviation * 2) + 1) - max_abs_deviation;
        }
        return n + rand_deviation;
    }

    static public void generateClasses(int num, int sizeFactor, float wiggle) {
        for (int j = 0; j < num; j++) {
            String className = nameClass(j);
            Utils.createRandomClass(className, sizeFactor, wiggle);
            if (j % 100 == 0) {
                System.out.print("*");
            }
        }
        System.out.println(".");
    }

    static void createRandomClass(String classname, int sizeFactor, float wiggle) {
        int sizeFactorFluctuated = fluctuate(sizeFactor, wiggle);
        sizeFactorFluctuated = Integer.max(1, sizeFactorFluctuated);
        String code = Utils.makeRandomSource(sizeFactorFluctuated).replaceAll("CLASSNAME", classname);
        boolean success = InMemoryJavaFileManager.theFileManager().compileSingleFile(classname, code);
        assert(success);
    }

    static public void createClassFromSource(String classname, String source) {
        boolean success = InMemoryJavaFileManager.theFileManager().compileSingleFile(classname, source);
        assert(success);
    }

    static public String makeRandomSource(int size) {
        return makeRandomSource(size, defaultWiggle);
    }

    static public String makeRandomSource(int size, float wiggle) {

        StringBuilder bld = new StringBuilder();
        bld.append("public class CLASSNAME {\n");
        int spread = (int)(size * wiggle);
        int size_with_wiggle = 0;
        if (spread > 0) {
            int offset = rand.nextInt(spread);
            size_with_wiggle = Math.min(size + (offset - spread / 2), 1);
        } else {
            size_with_wiggle = size;
        }
        for (int i = 0; i < size_with_wiggle; i ++) {
            bld.append("public int i" + i + " = " + i * 3 + ";\n");
            bld.append("public String s" + i + " = \"hallo" + i + "\";\n");
            bld.append("public byte[] b" + i + " = new byte[] {");
            for (int j = 0; j < 10 + (j % 30); j ++) {
                bld.append("0x" + Integer.toHexString(j % 0x80) + ", ");
            }

            bld.append("0x17};\n");
        }
        for (int i = 0; i < size; i ++) {
            bld.append("public int get_i" + i + "() { return i" + i + "; }\n");
            bld.append("public String get_s" + i + "() { return s" + i + "; }\n");
            bld.append("public byte[] get_b" + i + "() { return b" + i + "; }\n");
        }
        bld.append("};");
        return bld.toString();
    }


}