package de.stuefe.repros.metaspace;

import de.stuefe.repros.metaspace.internals.InMemoryClassLoader;
import de.stuefe.repros.metaspace.internals.Utils;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * This test will create an artificial Metaspace usage spike by generating a ton of small loaders
 * in order to analyze the Metaspace overhead for small loaders
 */


@CommandLine.Command(name = "SmallLoadersTest", mixinStandardHelpOptions = true,
        description = "SmallLoadersTest repro.")
public class SmallLoadersTest implements Callable<Integer> {

    public static void main(String... args) {
        int exitCode = new CommandLine(new SmallLoadersTest()).execute(args);
        System.exit(exitCode);
    }

    private static String nameClass(int number) {
        return "myclass_" + number;
    }

    int num_generations = 10;

    @CommandLine.Option(names = { "--num-loaders" }, defaultValue = "80",
            description = "Number of loaders per generation.")
    int num_loaders_per_generation;

    @CommandLine.Option(names = { "--num-classes" }, defaultValue = "100",
            description = "Number of classes per loader.")
    int num_classes_per_loader;

    float interleave = 0.4f;
    int loads_per_step;
    int class_size_factor = 10;
    boolean auto_yes = true;
    boolean nowait = false;
    int repeat_cycles = 0;
    int time_raise = 10;
    int time_top = 10;
    int time_falling = 3;
    int time_bottom = 60;
    int time_prepost = 10;
    float wiggle = 0.2f;
    float timefactor = 1.0f;

    void waitForKeyPress(String message, int secs) {
        secs = (int)(timefactor * (float)secs);
        secs = Integer.max(0, secs);

        if (message != null) {
            System.out.println(message);
        }
        System.out.print("<press key>");
        if (auto_yes) {
            System.out.print (" ... (auto-yes) ");
            if (secs > 0 && nowait == false) {
                System.out.print("... waiting " +secs + " secs ...");
                try {
                    Thread.sleep(secs * 1000);
                } catch (InterruptedException e) {
                }
            }
        } else {
            try {
                System.in.read();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println (" ... continuing.");
    }

    class LoaderInfo {
        ClassLoader cl = new InMemoryClassLoader("myloader", null);
        ArrayList<Class> loaded_classes = new ArrayList<>();
    }

    LoaderInfo[] loaders;

    boolean fullyLoaded() {
        for (int n = 0; n < loaders.length; n ++) {
            if (loaders[n] == null || loaders[n].loaded_classes.size() < num_classes_per_loader) {
                return false;
            }
        }
        return true;
    }

    private void loadInterleavedLoaders() throws ClassNotFoundException {

        // This "refills" the metaspace; note that this function should not have any effect on
        // loaders which are still left over from the last loading wave.

        int total_loaders = num_loaders_per_generation * num_generations;

        // (Re)create Loaders
        for (int loader = 0; loader < total_loaders; loader ++) {
            if (loaders[loader] == null) {
                loaders[loader] = new LoaderInfo();
            }
        }

        // Load classes
        do {
            for (int loader = 0; loader < total_loaders; loader ++) {
                int loaded = loaders[loader].loaded_classes.size();
                int toLoad = Integer.min(loads_per_step, num_classes_per_loader - loaded);
                for (int nclass = 0; nclass < toLoad; nclass++) {
                    String classname = nameClass(loaded + nclass);
                    Class<?> clazz = Class.forName(classname, true, loaders[loader].cl);
                    loaders[loader].loaded_classes.add(clazz);
                }
            }
            if (time_raise > 0) {
                waitForKeyPress("Load step", time_raise);
            } else {
                System.out.print("*");
            }
        } while(!fullyLoaded());

        System.gc();
        System.gc();
        System.out.println(".");
    }

    void freeGeneration(int gen) {
        int start_offset = gen;
        for (int i = start_offset; i < loaders.length; i+= num_generations) {
            loaders[i] = null;
        }
    }

    @Override
    public Integer call() throws Exception {

        interleave = Float.max(0.001f, interleave);
        interleave = Float.min(1.0f, interleave);
        loads_per_step = (int) ((float) num_classes_per_loader * interleave);
        loads_per_step = Integer.max(1, loads_per_step);
        loads_per_step = Integer.min(num_classes_per_loader, loads_per_step);

        timefactor = Float.max(0.1f, timefactor);
        timefactor = Float.min(10.0f, timefactor);

        wiggle = Float.max(0.0f, wiggle);
        wiggle = Float.min(10.0f, wiggle);

        repeat_cycles = Integer.max(1, repeat_cycles);
        repeat_cycles = Integer.min(100, repeat_cycles);

        loaders = new LoaderInfo[num_loaders_per_generation * num_generations];

        System.out.println("Loader gens: " + num_generations + ".");
        System.out.println("Loaders per gen: " + num_loaders_per_generation + ".");
        System.out.println("Classes per loader: " + num_classes_per_loader + ".");
        System.out.println("Loads per step: " + loads_per_step + " (interleave factor " + interleave + ")");
        System.out.println("Class size: " + class_size_factor + ".");
        System.out.println("Wiggle factor: " + wiggle + ".");
        System.out.println("Time factor: " + timefactor + ".");
        System.out.println("Repeat cycles: " + repeat_cycles + ".");

        waitForKeyPress("Generate " + num_classes_per_loader + " classes...", 0);

        Utils.generateClasses(num_classes_per_loader, class_size_factor, wiggle);

        System.gc();
        System.gc();

        long start = System.currentTimeMillis();

        waitForKeyPress("Before start...", time_prepost);

        for (int cycle = 0; cycle < repeat_cycles; cycle++) {
            System.out.println("Cycle: " + cycle);

            // First spike
            loadInterleavedLoaders();

            // Wait at the top of the first spike to collect samples
            waitForKeyPress("At first spike...", time_top);

            // get rid of all but the last one
            for (int i = 0; i < num_generations - 1; i++) {
                waitForKeyPress("Before freeing generation " + i + "...", 0);
                freeGeneration(i);
                System.gc();
                //System.gc();
                waitForKeyPress("After freeing generation " + i + ".", time_falling);
            }

            waitForKeyPress("Depressed...", time_bottom);

            // This GC is just there to trigger a metaspace JFR sample on older JDKs :(
            System.gc();
            //System.gc();

        }

        // Now free all
        waitForKeyPress("Before freeing all generations ",0);
        for (int i = 0; i < num_generations; i++) {
            freeGeneration(i);
        }
        System.gc();

        waitForKeyPress("After finish...", time_prepost);

        long finish = System.currentTimeMillis();
        long timeElapsed = finish - start;
        System.out.println("Elapsed Time: " + timeElapsed + " ms");

        return 0;
    }

}
