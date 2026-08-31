package de.speedbanking.iban;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

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
 *   <li>{@code BuilderBenchmarks} ({@code bmb}): Throughput of IBAN <em>object creation</em>
 *       from bank code and account number components. Limited to libraries that expose a
 *       component-based builder ({@code speedbanking iban-commons} and {@code iban4j});
 *       {@code Apache Commons Validator}, {@code garvelink iban}, and {@code marcwrobel jbanking}
 *       offer no equivalent construction API and are therefore excluded from this group.</li>
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
            .include(IbanBenchmarks.class.getSimpleName() + ".*");

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
     * Common JVM arguments applied to every fork in all benchmark groups.
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
     * Benchmarks the object-creation throughput of the {@code speedbanking iban-commons} library,
     * building a fresh {@link de.speedbanking.iban.Iban} from bank code and account number
     * components for each entry of the dataset.
     *
     * @param state JMH state holding the bank code / account number dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static void run_IbanCommons_Build(BuilderState state, Blackhole bh) {
        for (int i = 0; i < state.bankCodes.length; i++) {
            bh.consume(de.speedbanking.iban.IbanRegistry.DE
                .<de.speedbanking.iban.IbanBuilder.StandardIbanBuilder>builder()
                .bankCode(state.bankCodes[i])
                .accountNumber(state.accountNumbers[i])
                .build());
        }
    }

    /**
     * Benchmarks the object-creation throughput of the {@code iban4j} library, building a
     * fresh {@link org.iban4j.Iban} from bank code and account number components for each
     * entry of the dataset.
     *
     * @param state JMH state holding the bank code / account number dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static void run_Iban4j_Build(BuilderState state, Blackhole bh) {
        for (int i = 0; i < state.bankCodes.length; i++) {
            bh.consume(new org.iban4j.Iban.Builder()
                .countryCode(org.iban4j.CountryCode.DE)
                .bankCode(state.bankCodes[i])
                .accountNumber(state.accountNumbers[i])
                .build());
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
     * JMH state holding a dataset of DE bank code / account number component pairs used to
     * drive the {@link BuilderBenchmarks} group.
     * <p>
     * DE is used as the representative country because its BBAN structure (bank code + account
     * number, no national check digit) is uniformly supported by both {@code speedbanking
     * iban-commons} and {@code iban4j}, keeping the comparison apples-to-apples.
     */
    @State(Scope.Benchmark)
    public static class BuilderState {

        private static final int DE_BANK_CODE_LENGTH      = 8;
        private static final int DE_ACCOUNT_NUMBER_LENGTH = 10;

        int      targetSize = TARGET_SIZE;

        /**
         * Randomly generated, numeric DE bank codes ({@value #DE_BANK_CODE_LENGTH} digits each).
         */
        String[] bankCodes;

        /**
         * Randomly generated, numeric DE account numbers ({@value #DE_ACCOUNT_NUMBER_LENGTH}
         * digits each).
         */
        String[] accountNumbers;

        @Setup(Level.Trial)
        public void setup() {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            bankCodes = new String[targetSize];
            accountNumbers = new String[targetSize];
            for (int i = 0; i < targetSize; i++) {
                bankCodes[i] = randomDigits(random, DE_BANK_CODE_LENGTH);
                accountNumbers[i] = randomDigits(random, DE_ACCOUNT_NUMBER_LENGTH);
            }

            System.out.println("INFO: " + getClass().getSimpleName() + " dataset ready (size: " + targetSize + ")");
        }

        private static String randomDigits(ThreadLocalRandom random, int length) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append((char) ('0' + random.nextInt(10)));
            }
            return sb.toString();
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

    /**
     * Benchmark group for IBAN <em>object creation</em> from bank code and account number
     * components.
     * <p>
     * All methods measure throughput ({@link Mode#Throughput}) in operations per second over
     * {@value IbanBenchmarks#TARGET_SIZE} DE bank code / account number pairs. Only libraries
     * exposing a component-based builder participate; see the class-level javadoc for details.
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
    public static class BuilderBenchmarks {

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmb1_IbanCommons(BuilderState state, Blackhole bh) {
            run_IbanCommons_Build(state, bh);
        }

        @Benchmark
        @OperationsPerInvocation(TARGET_SIZE)
        public void bmb2_Iban4j(BuilderState state, Blackhole bh) {
            run_Iban4j_Build(state, bh);
        }
    }

}

