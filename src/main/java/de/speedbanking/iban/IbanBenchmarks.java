package de.speedbanking.iban;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark suite comparing IBAN validation performance across different libraries.
 * <p>
 * Structure:
 * <ul>
 *   <li>{@code ValidBenchmarks} ({@code bmv}): Throughput on <em>valid</em> IBANs.</li>
 *   <li>{@code InvalidBenchmarks} ({@code bmi}): Throughput on <em>invalid</em> IBANs
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
 * java -jar target/iban-commons-benchmarks.jar IbanBenchmarks
 * # with GC profiler:
 * java -jar target/iban-commons-benchmarks.jar IbanBenchmarks -prof gc
 * </pre>
 */
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:VisibilityModifier"})
public final class IbanBenchmarks {

    /**
     * Number of IBANs generated per benchmark trial.
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
    private IbanBenchmarks() {
    }

    /**
     * Common JVM arguments applied to every fork in both benchmark groups.
     * <p>
     * Defined once here to avoid divergence between {@link ValidBenchmarks} and
     * {@link InvalidBenchmarks}. Both groups reference this constant via the
     * {@link Fork#jvmArgs()} attribute.
     * <ul>
     *   <li>{@code -Xms2G -Xmx2G}: fixed heap – eliminates heap-resize pauses.</li>
     *   <li>{@code -XX:+AlwaysPreTouch}: pre-faults all heap pages at JVM start so
     *       that OS page faults do not skew early measurement iterations.</li>
     *   <li>{@code -XX:+UseSerialGC}: zero background GC threads; no CPU competition
     *       with benchmark threads; deterministic, low-noise stop-the-world behaviour.</li>
     *   <li>{@code -XX:-StackTraceInThrowable}: suppresses stack-trace generation for
     *       libraries that use exceptions for control flow (e.g. {@code iban4j}), isolating
     *       pure algorithmic cost. Not representative of production behaviour.</li>
     * </ul>
     */
    static final String[] FORK_JVM_ARGS = {
        "-Xms2G",
        "-Xmx2G",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseSerialGC",
        "-XX:-StackTraceInThrowable"
    };

    /**
     * Benchmarks the validation throughput of the {@code speedbanking iban-commons} library.
     *
     * @param state JMH state holding the IBAN dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_IbanCommons(T state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(de.speedbanking.iban.IbanValidator.isValid(iban));
        }
    }

    /**
     * Benchmarks the validation throughput of the {@code iban4j} library.
     *
     * @param state JMH state holding the IBAN dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_Iban4j(T state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(org.iban4j.IbanUtil.isValid(iban));
        }
    }

    /**
     * Benchmarks the validation throughput of the {@code Apache Commons Validator} library.
     * <p>
     * The Apache validator uses regex-based IBAN validation and is obtained as a singleton
     * via {@link org.apache.commons.validator.routines.IBANValidator#getInstance()}.
     *
     * @param state JMH state holding the IBAN dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_Apache(T state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(state.apacheValidator.isValid(iban));
        }
    }

    /**
     * Benchmarks the throughput of the {@code garvelink iban} library.
     * <p>
     * {@code IBAN.parse()} throws an exception for invalid input; the exception is
     * intentionally swallowed – the rejection path is the dominant path in the invalid
     * benchmark, and the happy path dominates in the valid benchmark.
     *
     * @param state JMH state holding the IBAN dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_Garvelink(T state, Blackhole bh) {
        for (String iban : state.ibans) {
            try {
                bh.consume(nl.garvelink.iban.IBAN.parse(iban));
            } catch (Exception ignored) {
                // expected for every entry in the invalid dataset
            }
        }
    }

    /**
     * Benchmarks the validation throughput of the {@code marcwrobel jbanking} library.
     *
     * @param state JMH state holding the IBAN dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_JBanking(T state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(fr.marcwrobel.jbanking.iban.Iban.isValid(iban));
        }
    }

    /**
     * Shared base state for all benchmark groups.
     * <p>
     * Handles generation of the IBAN dataset and initialisation of third-party validators.
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
         * The generated IBAN array iterated by all benchmark methods.
         * <p>
         * Each entry is stored in its compact, unformatted form
         * (e.g. {@code DE89370400440532013000}).
         */
        String[] ibans;

        /**
         * Singleton instance of the Apache Commons IBAN validator.
         * <p>
         * Obtained once during {@link #setup()} and reused for all measurements
         * within a trial to avoid repeated instantiation overhead.
         */
        org.apache.commons.validator.routines.IBANValidator apacheValidator;

        BaseState withTargetSize(int targetSize) {
            this.targetSize = targetSize;
            return this;
        }

        /**
         * Initialises the shared benchmark state at trial level.
         * <p>
         * The following steps are performed in order:
         * <ol>
         *   <li>Generate {@value IbanBenchmarks#TARGET_SIZE} random IBANs.</li>
         *   <li>Obtain the Apache Commons validator singleton.</li>
         *   <li>Reset {@link IbanConfig} to its default to establish a pure ISO 7064
         *       Mod 97-10 baseline (disables NCD features, space formatting, and
         *       lowercase support).</li>
         *   <li>Invoke {@link #setupDetail()} for subclass-specific preparations.</li>
         * </ol>
         */
        @Setup(Level.Trial)
        public final void setup() {
            ibans = generateIbans(targetSize);

            apacheValidator = org.apache.commons.validator.routines.IBANValidator.getInstance();

            // disable space-formatting, lowercase support, and NCD features for a pure
            // ISO 7064 Mod 97-10 baseline comparison
            IbanConfig.reset(IbanConfig.DEFAULT);

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
         * Generates an array of random, compact IBANs via {@link RandomIban}.
         *
         * @param size number of IBANs to generate
         * @return array of length {@code size} containing compact IBAN strings
         */
        final String[] generateIbans(int size) {
            String[] result = new String[size];

            for (int i = 0; i < size; i++) {
                de.speedbanking.iban.Iban iban = de.speedbanking.iban.RandomIban.any();
                result[i] = iban.toString();
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

        /**
         * Removes a random number of characters at random positions from the input.
         * <p>
         * Between 1 and {@code maxRemove} characters are removed (subject to the
         * available length). Positions are drawn sequentially; indices are recalculated
         * after each removal.
         * <p>
         * Edge cases:
         * <ul>
         *   <li>If {@code input} is {@code null}, {@code null} is returned.</li>
         *   <li>If {@code input} is empty, an empty string is returned.</li>
         * </ul>
         *
         * @param input     the base IBAN string; may be {@code null}
         * @param maxRemove maximum number of characters to remove (at least 1 is always removed)
         * @return the shortened string, or {@code null} if {@code input} is {@code null}
         */
        String removeRandomChars(CharSequence input, int maxRemove) {
            if (input == null) {
                return null;
            } else if (input.length() == 0) {
                return "";
            }

            StringBuilder sb = new StringBuilder(input);
            int count = random.nextInt(1, Math.min(input.length(), maxRemove + 1));

            for (int i = 0; i < count; i++) {
                sb.deleteCharAt(random.nextInt(sb.length()));
            }

            return sb.toString();
        }
    }

    /**
     * JMH state holding a dataset of exclusively <em>valid</em> IBANs.
     */
    @State(Scope.Benchmark)
    public static class ValidState extends BaseState {
    }

    /**
     * JMH state holding a dataset of exclusively <em>invalid</em> IBANs.
     * <p>
     * Starting from an initially valid dataset, all entries are corrupted in
     * {@link #setupDetail()} so that every entry fails validation. This allows
     * measuring the pure rejection overhead of each library.
     */
    @State(Scope.Benchmark)
    public static class InvalidState extends BaseState {

        /**
         * Corrupts the generated IBAN dataset so that every entry fails validation.
         */
        @Override
        protected void setupDetail() {
            for (int i = 0; i < ibans.length; i++) {
                ibans[i] = RandomIban.invalidString(ibans[i], random);
            }
        }
    }

    /**
     * Benchmark group for validating <em>valid</em> IBANs.
     * <p>
     * All methods measure throughput ({@link Mode#Throughput}) in operations per second
     * over a dataset of {@value IbanBenchmarks#TARGET_SIZE} valid IBANs.
     */
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 5, time = 2)
    @Measurement(iterations = 5, time = 2)
    @Fork(value = 3, jvmArgs = {
        "-Xms2G",
        "-Xmx2G",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseSerialGC",
        "-XX:-StackTraceInThrowable"
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
        public void bmv3_Apache(ValidState state, Blackhole bh) {
            run_Apache(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmv4_Garvelink(ValidState state, Blackhole bh) {
            run_Garvelink(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmv5_JBanking(ValidState state, Blackhole bh) {
            run_JBanking(state, bh);
        }

    }

    /**
     * Benchmark group for rejecting <em>invalid</em> IBANs.
     * <p>
     * All methods measure throughput ({@link Mode#Throughput}) in operations per second
     * over a dataset of {@value IbanBenchmarks#TARGET_SIZE} invalid IBANs. This isolates
     * how efficiently each library rejects erroneous input – often the dominant code path
     * in production systems.
     */
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 5, time = 2)
    @Measurement(iterations = 5, time = 2)
    @Fork(value = 3, jvmArgs = {
        "-Xms2G",
        "-Xmx2G",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseSerialGC",
        "-XX:-StackTraceInThrowable"
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
        public void bmi3_Apache(InvalidState state, Blackhole bh) {
            run_Apache(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmi4_Garvelink(InvalidState state, Blackhole bh) {
            run_Garvelink(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmi5_JBanking(InvalidState state, Blackhole bh) {
            run_JBanking(state, bh);
        }
    }

}

