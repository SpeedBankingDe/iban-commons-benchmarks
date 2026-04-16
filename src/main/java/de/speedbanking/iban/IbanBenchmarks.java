package de.speedbanking.iban;

import de.speedbanking.util.Iso3166Alpha2;

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
     * Benchmarks the validation throughput of the {@code speedbanking iban-commons} library
     * against valid IBANs.
     *
     * @param state JMH state holding the valid IBAN dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_IbanCommons(T state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(de.speedbanking.iban.IbanValidator.isValid(iban));
        }
    }

    /**
     * Benchmarks the validation throughput of the {@code iban4j} library against valid IBANs.
     *
     * @param state JMH state holding the valid IBAN dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_Iban4j(T state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(org.iban4j.IbanUtil.isValid(iban));
        }
    }

    /**
     * Benchmarks the validation throughput of the {@code Apache Commons Validator} library
     * against valid IBANs.
     * <p>
     * The Apache validator uses regex-based IBAN validation and is obtained as a singleton
     * via {@link org.apache.commons.validator.routines.IBANValidator#getInstance()}.
     *
     * @param state JMH state holding the valid IBAN dataset
     * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
     */
    static <T extends BaseState> void run_Apache(T state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(state.apacheValidator.isValid(iban));
        }
    }

    /**
     * Benchmarks the rejection throughput of the {@code garvelink iban} library
     * against invalid IBANs.
     * <p>
     * Since {@code IBAN.parse()} throws an exception for invalid input, the exception is
     * intentionally swallowed here – the rejection path is the dominant path in this benchmark.
     *
     * @param state JMH state holding the invalid IBAN dataset
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
     * Benchmarks the validation throughput of the {@code marcwrobel jbanking} library
     * against valid IBANs.
     *
     * @param state JMH state holding the valid IBAN dataset
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
     * Handles generation of the IBAN dataset and initialization of third-party validators.
     * Subclasses may perform additional setup by overriding {@link #setupDetail()}.
     */
    public abstract static class BaseState {

        /**
         * Thread-local random number generator for contention-free parallel data generation.
         * <p>
         * {@link ThreadLocalRandom} is preferred over {@link java.util.Random} because it
         * requires no synchronization and therefore introduces no artificial contention points
         * in JMH benchmarks.
         */
        final ThreadLocalRandom random     = ThreadLocalRandom.current();

        int                     targetSize = TARGET_SIZE;

        /**
         * The generated IBAN array iterated by all benchmark methods.
         * <p>
         * Each entry is either a compact IBAN (e.g. {@code DE89370400440532013000})
         * or a formatted IBAN with spaces (e.g. {@code DE89 3704 0044 0532 0130 00}),
         * randomly mixed at a 1:1 ratio to cover both common input formats.
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
         * Initializes the shared benchmark state at trial level.
         * <p>
         * The following steps are performed in order:
         * <ol>
         *   <li>Generate {@value IbanBenchmarks#TARGET_SIZE} random IBANs.</li>
         *   <li>Obtain the Apache Commons validator singleton.</li>
         *   <li>Disable NCD features ({@code NCD_CALCULATE} and {@code NCD_VALIDATE})
         *       to establish a baseline against standard ISO 7064 Mod 97-10 validation.</li>
         *   <li>Invoke {@link #setupDetail()} for subclass-specific preparations.</li>
         * </ol>
         */
        @Setup(Level.Trial)
        public final void setup() {
            ibans = generateIbans(targetSize);

            apacheValidator = org.apache.commons.validator.routines.IBANValidator.getInstance();

            // disable space-formatting, lowercase support, and NCD features for a pure ISO 7064 Mod 97-10 baseline comparison
            IbanConfig.reset(IbanConfig.DEFAULT);

            setupDetail();

            System.out.println("INFO: " + getClass().getSimpleName() + " dataset ready (size: " + TARGET_SIZE + ")");
        }

        /**
         * Optional hook for subclass-specific setup logic.
         * <p>
         * Called by {@link #setup()} after common initialization is complete.
         * The default implementation is a no-op; subclasses may override this method
         * to, for example, corrupt the dataset for rejection benchmarks.
         */
        protected void setupDetail() {}

        /**
         * Generates a mixed set of random IBANs based on {@link IbanRegistry}.
         * <p>
         * For each entry, it is randomly decided whether the IBAN is stored in its
         * compact form ({@link Iban#toString()}) or formatted with spaces
         * ({@link Iban#toFormattedString()}), ensuring the dataset covers both
         * common input formats in equal proportion.
         *
         * @param size number of IBANs to generate
         * @return array of length {@code size} containing random IBAN strings
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
     * <p>
     * The dataset is generated by {@link BaseState#setup()} and contains compact
     * and formatted IBANs in random mixture.
     */
    @State(Scope.Benchmark)
    public static class ValidState extends BaseState {
    }

    /**
     * JMH state holding a dataset of exclusively <em>invalid</em> IBANs.
     * <p>
     * Starting from an initially valid dataset, all entries are corrupted in
     * {@link #setupDetail()} via {@link #sabotageIban(StringBuilder)} until
     * {@link de.speedbanking.iban.Iban#isValid(String)} returns {@code false}
     * for every entry. This allows measuring the pure rejection overhead of each library.
     */
    @State(Scope.Benchmark)
    public static class InvalidState extends BaseState {

        /** Minimum required IBAN length: country code (2) + check digits (2). */
        private static final int MIN_IBAN_BASE_LENGTH = de.speedbanking.iban.IbanRegistry.MIN_IBAN_BASE_LENGTH;

        /** Index of the first IBAN check digit within the full IBAN string (position 3, zero-based index 2). */
        private static final int INDEX_CHECK_DIGIT1   = de.speedbanking.iban.IbanRegistry.INDEX_CHECK_DIGIT1;

        /** Index of the second IBAN check digit within the full IBAN string (position 4, zero-based index 3). */
        private static final int INDEX_CHECK_DIGIT2   = de.speedbanking.iban.IbanRegistry.INDEX_CHECK_DIGIT2;

        /** Start index of the Basic Bank Account Number (BBAN) within the IBAN string (position 5, zero-based index 4). */
        private static final int INDEX_BBAN           = de.speedbanking.iban.IbanRegistry.INDEX_BBAN;

        /**
         * Corrupts the generated IBAN dataset so that every entry fails validation.
         * <p>
         * For each IBAN, {@link #sabotageIban(StringBuilder)} is called repeatedly
         * until {@link de.speedbanking.iban.Iban#isValid(String)} returns {@code false},
         * ensuring no accidentally still-valid entry remains in the dataset.
         */
        @Override
        protected void setupDetail() {
            for (int i = 0; i < ibans.length; i++) {
                StringBuilder sb = new StringBuilder(ibans[i]);

                while (de.speedbanking.iban.Iban.isValid(sabotageIban(sb))) {
                    // sabotageIban mutates sb in-place; repeat until validation fails
                    continue;
                }

                ibans[i] = sb.toString();
            }
        }

        /**
         * Corrupts the given IBAN using a randomly selected sabotage strategy.
         * <p>
         * The method modifies the {@link StringBuilder} <em>in-place</em> and returns it.
         * Six strategies are applied with equal probability:
         * <ol>
         *   <li><strong>Tamper check digit</strong> – one of the two check digits (position 3 or 4)
         *       is incremented by 1 (wrapping: {@code '9'} → {@code '0'}) to trigger a Mod-97
         *       checksum failure.</li>
         *   <li><strong>Invalid country code</strong> – the first two characters are replaced with
         *       {@code "XY"}, a non-registered ISO 3166 Alpha-2 code.</li>
         *   <li><strong>Mismatched ISO code</strong> – the first two characters are replaced with a
         *       valid but randomly chosen {@link Iso3166Alpha2} code that does not match the
         *       existing BBAN format.</li>
         *   <li><strong>Structural violation in BBAN</strong> – a letter ({@code 'A'}) is injected
         *       at a random position within the BBAN section to break the expected character
         *       pattern.</li>
         *   <li><strong>Transposition error</strong> – two adjacent characters are swapped,
         *       simulating a classic human keying mistake.</li>
         *   <li><strong>Illegal length</strong> – the string is truncated to below
         *       {@link #MIN_IBAN_BASE_LENGTH}, making it structurally invalid.</li>
         * </ol>
         *
         * @param iban the IBAN string to sabotage as a {@link StringBuilder};
         *             modified in-place
         * @return the same {@link StringBuilder} instance after mutation
         * @throws IllegalStateException if an unhandled strategy index is encountered
         *                               (should never occur in practice)
         */
        StringBuilder sabotageIban(StringBuilder iban) {
            switch (random.nextInt(6)) {
                case 0 -> {
                    // increment one check digit (position 3 or 4) to trigger a Mod-97 failure
                    int cdIdx = random.nextBoolean() ? INDEX_CHECK_DIGIT1 : INDEX_CHECK_DIGIT2;
                    char c = iban.charAt(cdIdx);
                    iban.setCharAt(cdIdx, c == '9' ? '0' : (char) (c + 1));
                }
                case 1 -> {
                    // replace country code with non-existent "XY"
                    iban.setCharAt(0, 'X');
                    iban.setCharAt(1, 'Y');
                }
                case 2 -> {
                    // replace country code with a valid but mismatched ISO code
                    Iso3166Alpha2[] countries = de.speedbanking.util.Iso3166Alpha2.values();
                    String randomIso = countries[random.nextInt(countries.length)].name();
                    iban.setCharAt(0, randomIso.charAt(0));
                    iban.setCharAt(1, randomIso.charAt(1));
                }
                case 3 -> {
                    // inject a letter into the numeric BBAN section to cause a structural violation
                    if (iban.length() > INDEX_BBAN) {
                        int pos = INDEX_BBAN
                            + random.nextInt(iban.length() - INDEX_BBAN);
                        iban.setCharAt(pos, 'A');
                    }
                }
                case 4 -> {
                    // classic transposition: swap two adjacent characters
                    int p = random.nextInt(iban.length() - 1);
                    char tmp = iban.charAt(p);
                    iban.setCharAt(p, iban.charAt(p + 1));
                    iban.setCharAt(p + 1, tmp);
                }
                case 5 -> {
                    // truncate below minimum length to produce a structurally invalid IBAN
                    if (iban.length() > MIN_IBAN_BASE_LENGTH) {
                        iban.setLength(MIN_IBAN_BASE_LENGTH - 1);
                    }
                }
                default -> throw new IllegalStateException("Unexpected strategy");
            }
            return iban;
        }
    }

    /**
     * Benchmark group for validating <em>valid</em> IBANs.
     * <p>
     * All methods measure throughput ({@link Mode#Throughput}) in operations per second
     * over a dataset of {@value IbanBenchmarks#TARGET_SIZE} valid IBANs. Each fork starts
     * in a fresh JVM process; generational ZGC minimizes GC-induced measurement artifacts.
     */
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 5, time = 2)
    @Measurement(iterations = 5, time = 2)
    @Fork(value = 2, jvmArgs = {
        "-Xms2G",
        "-Xmx2G",
        // "-XX:+UseZGC",
        // "-XX:+ZGenerational",
        "-XX:+UseParallelGC",
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

        /**
         * Benchmarks the validation throughput of the {@code garvelink iban} library
         * against valid IBANs.
         * <p>
         * The library uses an object-oriented parsing approach: {@code IBAN.parse()} throws
         * an exception for invalid input. Since the dataset contains only valid IBANs,
         * the {@code catch} block is present purely for completeness.
         *
         * @param state JMH state holding the valid IBAN dataset
         * @param bh    {@link Blackhole} to prevent dead-code elimination by the JIT compiler
         */
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
    @Measurement(iterations = 4, time = 2)
    @Fork(value = 2, jvmArgs = {
        "-Xms2G",
        "-Xmx2G",
        // "-XX:+UseZGC",
        // "-XX:+ZGenerational",
        "-XX:+UseParallelGC",
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

    public static void main(String[] args) {
        BaseState validState = new ValidState().withTargetSize(100);
        validState.setup();

        for (String iban : validState.ibans) {
            boolean valid = fr.marcwrobel.jbanking.iban.Iban.isValid(iban);
            (valid ? System.out : System.err).printf("IBAN is %s : %s%n", valid ? "valid  " : "INVALID", iban);
        }
    }

}

