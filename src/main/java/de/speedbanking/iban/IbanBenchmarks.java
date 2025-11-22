package de.speedbanking.iban;

import org.apache.commons.validator.routines.IBANValidator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * JMH Microbenchmark for comparing IBAN validation and object creation performance
 * across different libraries (iban-commons, iban4j, Apache Commons Validator).
 * <p>
 * Benchmarks are run in throughput mode to measure operations per unit of time.
 * <p>
 * To execute this test using Maven:
 * <pre>
 *   {@code mvn clean package -P jmh}
 *   {@code java -jar target/iban-commons-benchmarks-1.0.0-SNAPSHOT.jar IbanBenchmarks -prof gc}
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-Xms2048m", "-Xmx2048m"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings("checkstyle:MethodName")
public class IbanBenchmarks {

    private static final int           TARGET_SIZE           = 1_000_000;

    // avoid creation of Apache IBAN validator on every call to isValid() for fairness.
    private static final IBANValidator APACHE_IBAN_VALIDATOR = new IBANValidator();

    /**
     * Helper class to manage and provide test data for the benchmark methods
     * to ensure the data array is generated once per benchmark run (at {@link Level#Trial}).<br>
     * The list of IBAN strings contains both valid and intentionally corrupted (invalid) IBANs.
     */
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        private final List<String> ibans = new ArrayList<>(TARGET_SIZE);

        /**
         * Setup method to generate the list of IBANs before the benchmark trials begin.<br>
         * The list contains a mix of normalized/formatted and valid/invalid IBANs.
         */
        @Setup(Level.Trial)
        public void setup() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            List<IbanRegistry> countries = Arrays.asList(IbanRegistry.values());

            for (int i = 0; i < TARGET_SIZE; i++) {
                // generate a valid IBAN
                Iban ibanObject = RandomIban.of(getRandomListEntry(countries));
                String iban;

                // 1. Create IBANs: 50% normalized, 50% formatted (with spaces)
                if (random.nextBoolean()) {
                    // normalized (standard format, no spaces)
                    iban = ibanObject.toString();
                } else {
                    // formatted (with spaces)
                    iban = ibanObject.toFormattedString();
                }

                // 2. Introduce invalid IBANs (approx. 25% invalid)
                if (random.nextInt() % 4 == 0) {
                    if (random.nextBoolean()) {
                        // corrupt by swapping characters (e.g., to break checksum)
                        iban = swapRandomChars(iban);
                    } else {
                        // Corrupt by removing up to 3 characters (e.g., to break length validation)
                        iban = removeRandomChars(iban, 3);
                    }
                }

                ibans.add(iban);
            }
        }

        /**
         * Provides the pre-generated list of IBAN strings for the benchmark.
         * @return A list of IBAN strings.
         */
        List<String> getIbans() {
            return ibans;
        }
    }

    /**
     * Measures the throughput of the pure validation check (boolean return) of the **iban-commons** library.
     *
     * @param state The benchmark state providing the IBAN data.
     * @param bh The Blackhole to consume the result and prevent DCE.
     */
    @Benchmark
    public void bm1a_IbanCommons_IsValid(final BenchmarkState state, final Blackhole bh) {
        for (String iban : state.getIbans()) {
            // measurement includes validation only
            bh.consume(de.speedbanking.iban.Iban.isValid(iban));
        }
    }

    /**
     * Measures the throughput of object creation, which includes full validation and
     * object construction ({@link de.speedbanking.iban.Iban#tryParse(CharSequence)}) of the **iban-commons** library.
     * <p>
     * Dead Code Elimination (DCE) is prevented by using the Blackhole to consume
     * the result.
     *
     * @param state The benchmark state providing the IBAN data.
     * @param bh The Blackhole to consume the result and prevent DCE.
     */
    @Benchmark
    public void bm1b_IbanCommons_ObjectCreation(final BenchmarkState state, final Blackhole bh) {
        for (String iban : state.getIbans()) {
            // must consume the returned Iban object/Optional to prevent DCE
            bh.consume(de.speedbanking.iban.Iban.tryParse(iban));
        }
    }

    /**
     * Measures the throughput of validation using **iban4j**.
     *
     * @param state The benchmark state providing the IBAN data.
     * @param bh The Blackhole to consume the result or exception and prevent DCE.
     */
    @Benchmark
    public void bm2a_Iban4j_IsValid(final BenchmarkState state, final Blackhole bh) {
        for (String iban : state.getIbans()) {
            bh.consume(org.iban4j.IbanUtil.isValid(iban));
        }
    }

    /**
     * Measures the throughput of validation and object creation using **iban4j**.<br>
     * Validation errors are consumed as exceptions.
     *
     * @param state The benchmark state providing the IBAN data.
     * @param bh The Blackhole to consume the result or exception and prevent DCE.
     */
    @Benchmark
    public void bm2b_Iban4j_ObjectCreation(final BenchmarkState state, final Blackhole bh) {
        for (String iban : state.getIbans()) {
            try {
                // measurement includes validation and object creation
                bh.consume(org.iban4j.Iban.valueOf(iban));
            } catch (Exception ex) {
                // consume the exception path result
                bh.consume(ex);
            }
        }
    }

    /**
     * Measures the throughput of validation using the static instance of **Apache Commons Validator**.
     *
     * @param state The benchmark state providing the IBAN data.
     * @param bh The Blackhole to consume the result and prevent DCE.
     */
    @Benchmark
    public void bm3a_ApacheCommons_IsValid(final BenchmarkState state, final Blackhole bh) {
        for (String iban : state.getIbans()) {
            bh.consume(APACHE_IBAN_VALIDATOR.isValid(iban));
        }
    }

    /**
     * Retrieves a random element from the provided list.
     *
     * @param <T> The type of elements in the list.
     * @param list The list from which a random element should be selected. Must not be {@code null} or empty.
     * @return A randomly selected element of type {@code T} from the list.
     * @throws IllegalArgumentException if the list is {@code null} or empty.
     */
    static <T> T getRandomListEntry(final List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List must not be null or empty");
        }

        final int sz = list.size();

        return list.get(1 == sz ? 0 : ThreadLocalRandom.current().nextInt(sz));
    }

    /**
     * Swaps two random, distinct characters in the input string to intentionally
     * corrupt a string such as an IBAN, making it invalid (e.g., failing a checksum validation).
     *
     * @param input The string to corrupt. Must have a length of at least 2.
     * @return The corrupted string, or the original string if length < 2 or input is {@code null}.
     */
    static String swapRandomChars(final String input) {
        if (input == null || input.length() < 2) {
            return input;
        }

        int len = input.length();
        char[] chars = input.toCharArray();

        int index1 = ThreadLocalRandom.current().nextInt(len);
        // Generates an index in the range [0, len - 2].
        int index2 = ThreadLocalRandom.current().nextInt(len - 1);

        // Map the shortened range index back to the full range, skipping index1.
        // This ensures index2 != index1 and maintains uniform probability.
        if (index2 >= index1) {
            index2++;
        }

        char temp = chars[index1];
        chars[index1] = chars[index2];
        chars[index2] = temp;

        return new String(chars);
    }

    /**
     * Removes a random number of characters (between 1 and {@code maxCharsRemove})
     * from the input string to intentionally corrupt it (e.g., failing a length validation).<br>
     * The number of characters removed will be at most the length of the string minus 1.
     *
     * @param input          The string to corrupt.
     * @param maxCharsRemove The maximum number of characters to remove (must be >= 1).
     * @return The corrupted string.
     */
    static String removeRandomChars(final String input, final int maxCharsRemove) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        final int len = input.length();
        // determine the actual number of characters to remove, min 1, max up to the length - 1
        // to ensure the resulting string is not empty and an actual removal occurs.
        int charsToRemove = ThreadLocalRandom.current().nextInt(1, Math.min(len, maxCharsRemove + 1));

        // create a list of indices to remove
        List<Integer> indicesToRemove = IntStream.range(0, len)
            .boxed()
            .collect(Collectors.toCollection(ArrayList::new));

        // shuffle the list and pick the first 'charsToRemove' indices
        Collections.shuffle(indicesToRemove, ThreadLocalRandom.current());
        Collections.sort(indicesToRemove.subList(0, charsToRemove));

        // build the new string by skipping the characters at the selected indices
        StringBuilder sb = new StringBuilder(len - charsToRemove);
        int removeIndexCounter = 0;
        for (int i = 0; i < len; i++) {
            if (removeIndexCounter < charsToRemove && i == indicesToRemove.get(removeIndexCounter)) {
                removeIndexCounter++;
            } else {
                sb.append(input.charAt(i));
            }
        }

        return sb.toString();
    }

}
