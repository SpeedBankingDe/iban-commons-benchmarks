package de.speedbanking.bic;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark suite comparing BIC validation performance across different libraries.
 * <p>
 * Structure:
 * <ul>
 *   <li>{@code ValidBenchmarks} ({@code bmv}): Throughput on <em>valid</em> BICs.</li>
 *   <li>{@code InvalidBenchmarks} ({@code bmi}): Throughput on <em>invalid</em> BICs
 *       (rejection cost).</li>
 * </ul>
 * <p>
 * Running both groups together gives a realistic picture of library performance across
 * the full spectrum of real-world input.
 * <p>
 * <strong>Note on {@code -XX:-StackTraceInThrowable}:</strong> This flag eliminates the
 * JVM cost of stack trace generation in libraries that use exceptions for control flow
 * (e.g. {@code iban4j}). It isolates pure algorithm performance, but does <em>not</em>
 * reflect production behaviour where stack traces are enabled.
 * <p>
 * <strong>Note on {@code -XX:+UseSerialGC}:</strong> SerialGC has zero background threads
 * and therefore introduces no GC-induced CPU interference with benchmark threads. This
 * minimises measurement noise in {@code gc.alloc.rate.norm} and is the recommended GC
 * for microbenchmarks. ZGC/G1 are better suited for low-latency production workloads, not
 * for benchmarking.
 * <p>
 * To execute this benchmark suite using Maven:
 * <pre>
 * mvn clean package
 * # normal run:
 * java -jar target/iban-commons-benchmarks.jar BicBenchmarks
 * # with GC profiler:
 * java -jar target/iban-commons-benchmarks.jar BicBenchmarks -prof gc
 * </pre>
 */
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:VisibilityModifier"})
public final class BicBenchmarks {

    /**
     * Number of BICs generated per benchmark trial.
     * <p>
     * The large value ensures that JIT warm-up effects and CPU cache influences
     * are averaged out over a sufficiently large dataset.
     */
    private static final int TARGET_SIZE = 1_000_000;

    /**
     * Private constructor – this class is not instantiable.
     * <p>
     * All benchmarks are defined as static inner classes using JMH annotations.
     */
    private BicBenchmarks() {
    }

    /**
     * Standard entry point for executing or profiling the benchmarks directly from the IDE.
     * <p>
     * Detection of an active IDE profiler runner is based on systemic properties or can be
     * toggled by modifying the boolean flag below. When profiling, forks are set to 0 to keep
     * execution inside the IDE's monitored JVM.
     *
     * @param args command line arguments passed to the runner
     * @throws Exception if the benchmark execution fails
     */
    public static void main(String[] args) throws Exception {
        boolean profileMode = true; // set to false for a normal non-profiled trial run in the IDE

        ChainedOptionsBuilder builder = new OptionsBuilder()
            .include(BicBenchmarks.class.getSimpleName() + ".*");

        if (profileMode) {
            System.out.println("INFO: Running in IDE Profiler Mode (Forks = 0, shortened iterations)");
            builder.forks(0)
                   .warmupIterations(2)
                   .measurementIterations(3);
        } else {
            builder.forks(1)
                   .warmupIterations(5)
                   .measurementIterations(5);
        }

        Options opt = builder.build();
        new Runner(opt).run();
    }

    /**
     * Common JVM arguments applied to every fork in both benchmark groups.
     * <p>
     * Declared as individual {@code String} constants – rather than a single array constant –
     * because Java annotation attributes only accept compile-time constant expressions, and an
     * array-typed field does not qualify; each constant below, however, does, so every
     * {@link Fork#jvmArgs()} attribute in this class references these directly instead of
     * duplicating the literal values.
     */
    static final String JVM_ARG_HEAP_MIN         = "-Xms2G";
    static final String JVM_ARG_HEAP_MAX         = "-Xmx2G";
    static final String JVM_ARG_ALWAYS_PRE_TOUCH = "-XX:+AlwaysPreTouch";
    static final String JVM_ARG_SERIAL_GC        = "-XX:+UseSerialGC";
    static final String JVM_ARG_NO_STACKTRACE    = "-XX:-StackTraceInThrowable";

    /**
     * Benchmarks the validation throughput of the {@code speedbanking iban-commons} library.
     *
     * @param state JMH state holding the BIC dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_IbanCommons(T state, Blackhole bh) {
        for (String bic : state.bics) {
            bh.consume(de.speedbanking.bic.Bic.isValid(bic));
        }
    }

    /**
     * Benchmarks the validation throughput of the {@code iban4j} library.
     * <p>
     * {@code BicUtil.validate()} throws an exception for invalid input; the exception is
     * intentionally swallowed – the rejection path is the dominant path in the invalid
     * benchmark, and the happy path dominates in the valid benchmark.
     *
     * @param state JMH state holding the BIC dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_Iban4j(T state, Blackhole bh) {
        for (String bic : state.bics) {
            try {
                org.iban4j.BicUtil.validate(bic);
                bh.consume(true);
            } catch (Exception ignored) {
                // expected for every entry in the invalid dataset
            }
        }
    }

    /**
     * Benchmarks the validation throughput of the {@code marcwrobel jbanking} library.
     *
     * @param state JMH state holding the BIC dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_JBanking(T state, Blackhole bh) {
        for (String bic : state.bics) {
            bh.consume(fr.marcwrobel.jbanking.bic.Bic.isValid(bic));
        }
    }

    /**
     * Shared base state for all benchmark groups.
     * <p>
     * Handles generation of the BIC dataset and initialisation of third-party validators.
     * Subclasses may perform additional setup by overriding {@link #setupDetail()}.
     */
    public abstract static class BaseState {

        /**
         * Thread-local random number generator for contention-free parallel data generation.
         * <p>
         * {@link ThreadLocalRandom} is preferred over {@link java.util.Random} because it
         * requires no synchronisation and therefore introduces no artificial contention points
         * in JMH benchmarks.
         */
        final ThreadLocalRandom random     = ThreadLocalRandom.current();

        int                     targetSize = TARGET_SIZE;

        /**
         * The generated BIC array iterated by all benchmark methods.
         * <p>
         * Each entry is stored in its compact, unformatted form (8 or 11 characters).
         */
        String[] bics;

        /**
         * Initialises the shared benchmark state at trial level.
         * <p>
         * The following steps are performed in order:
         * <ol>
         *   <li>Generate {@value BicBenchmarks#TARGET_SIZE} random BICs.</li>
         *   <li>Invoke {@link #setupDetail()} for subclass-specific preparations.</li>
         * </ol>
         */
        @Setup(Level.Trial)
        public final void setup() {
            bics = generateBics(targetSize);

            setupDetail();

            System.out.println("INFO: " + getClass().getSimpleName() + " dataset ready (size: " + targetSize + ")");
        }

        /**
         * Optional hook for subclass-specific setup logic.
         * <p>
         * Called by {@link #setup()} after common initialisation is complete.
         * The default implementation is a no-op; subclasses may override this method
         * to, for example, corrupt the dataset for rejection benchmarks.
         */
        protected void setupDetail() {}

        /**
         * Generates an array of random, compact BICs via {@link RandomBic}.
         *
         * @param size number of BICs to generate
         * @return array of length {@code size} containing compact BIC strings
         */
        final String[] generateBics(int size) {
            String[] result = new String[size];

            for (int i = 0; i < size; i++) {
                de.speedbanking.bic.Bic bic = de.speedbanking.bic.RandomBic.of();
                result[i] = bic.toString();
            }

            return result;
        }

        /**
         * Swaps two characters at distinct, randomly chosen positions within the given
         * character sequence.
         * <p>
         * The two indices are selected so that every unordered pair of positions has equal
         * probability of being chosen (Fisher-Yates-style two-index sampling).
         * <p>
         * Edge cases:
         * <ul>
         *   <li>If {@code input} is {@code null}, {@code null} is returned.</li>
         *   <li>If {@code input} has fewer than 2 characters, its string representation
         *       is returned unchanged.</li>
         * </ul>
         *
         * @param input the character sequence to transform; may be {@code null}
         * @return a new string with two characters swapped, the unchanged string for
         *         inputs shorter than 2, or {@code null} for a {@code null} input
         */
        String swapRandomChars(final CharSequence input) {
            if (input == null || input.length() < 2) {
                return input == null ? null : input.toString();
            }

            int len = input.length();
            char[] chars = input.toString().toCharArray();

            // pick two distinct indices with uniform probability
            int index1 = random.nextInt(len);
            int index2 = random.nextInt(len - 1);
            if (index2 >= index1) {
                index2++;
            }

            char temp = chars[index1];
            chars[index1] = chars[index2];
            chars[index2] = temp;

            return new String(chars);
        }
    }

    /**
     * JMH state holding a dataset of exclusively <em>valid</em> BICs.
     */
    @State(Scope.Benchmark)
    public static class ValidState extends BaseState {
    }

    /**
     * JMH state holding a dataset of exclusively <em>invalid</em> BICs.
     * <p>
     * Starting from an initially valid dataset, all entries are corrupted in
     * {@link #setupDetail()} so that every entry fails validation. This allows
     * measuring the pure rejection overhead of each library.
     */
    @State(Scope.Benchmark)
    public static class InvalidState extends BaseState {

        /**
         * Corrupts the generated BIC dataset so that every entry fails validation.
         */
        @Override
        protected void setupDetail() {
            for (int i = 0; i < bics.length; i++) {
                bics[i] = swapRandomChars(bics[i]);
            }
        }
    }

    /**
     * Benchmark group for validating <em>valid</em> BICs.
     * <p>
     * All methods measure throughput ({@link Mode#Throughput}) in operations per second
     * over a dataset of {@value BicBenchmarks#TARGET_SIZE} valid BICs.
     */
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 5, time = 2)
    @Measurement(iterations = 5, time = 2)
    @Fork(value = 3, jvmArgs = {
        JVM_ARG_HEAP_MIN,
        JVM_ARG_HEAP_MAX,
        JVM_ARG_ALWAYS_PRE_TOUCH,
        JVM_ARG_SERIAL_GC,
        JVM_ARG_NO_STACKTRACE
    })
    public static class ValidBenchmarks {

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmv1_IbanCommons(ValidState state, Blackhole bh) {
            run_IbanCommons(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmv2_Iban4j(ValidState state, Blackhole bh) {
            run_Iban4j(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmv3_JBanking(ValidState state, Blackhole bh) {
            run_JBanking(state, bh);
        }
    }

    /**
     * Benchmark group for rejecting <em>invalid</em> BICs.
     * <p>
     * All methods measure throughput ({@link Mode#Throughput}) in operations per second
     * over a dataset of {@value BicBenchmarks#TARGET_SIZE} invalid BICs. This isolates
     * how efficiently each library rejects erroneous input – often the dominant code path
     * in production systems.
     */
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 5, time = 2)
    @Measurement(iterations = 5, time = 2)
    @Fork(value = 3, jvmArgs = {
        JVM_ARG_HEAP_MIN,
        JVM_ARG_HEAP_MAX,
        JVM_ARG_ALWAYS_PRE_TOUCH,
        JVM_ARG_SERIAL_GC,
        JVM_ARG_NO_STACKTRACE
    })
    public static class InvalidBenchmarks {

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmi1_IbanCommons(InvalidState state, Blackhole bh) {
            run_IbanCommons(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmi2_Iban4j(InvalidState state, Blackhole bh) {
            run_Iban4j(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmi3_JBanking(InvalidState state, Blackhole bh) {
            run_JBanking(state, bh);
        }
    }

}
