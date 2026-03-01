package de.speedbanking.iban;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark for comparing IBAN validation and object creation performance
 * across different libraries (iban-commons, iban4j, Apache Commons Validator, Garvelink).
 * <p>
 * Two benchmark groups are provided:
 * <ul>
 *   <li>{@code bm1}–{@code bm4}: Throughput on <em>valid</em> IBANs only (best-case scenario).</li>
 *   <li>{@code bm5}–{@code bm8}: Throughput on <em>invalid</em> IBANs (worst-case / rejection cost).</li>
 * </ul>
 * Running both groups together gives a realistic picture of library performance across
 * the full spectrum of real-world input.
 * <p>
 * <strong>Note on {@code -XX:-StackTraceInThrowable}:</strong> This flag eliminates the
 * cost of JVM stack trace generation in libraries that use exceptions for control flow
 * (like {@code iban4j}). It isolates pure algorithm performance, but does <em>not</em>
 * reflect production behaviour where stack traces are enabled. For a production-realistic
 * measurement, re-run without this flag and compare results.
 * <p>
 * To execute this test using Maven:
 * <pre>
 *   {@code mvn clean package}
 *   {@code java -jar target/iban-commons-benchmarks.jar IbanBenchmarks -prof gc}
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 3, jvmArgs = {
    "-Xms2G",
    "-Xmx2G",
    "-XX:-StackTraceInThrowable"
})
@SuppressWarnings({"checkstyle:MethodName", "checkstyle:VisibilityModifier"})
public class IbanBenchmarks {

    private static final int TARGET_SIZE = 1_000_000;

    /**
     * State containing only valid IBANs for throughput measurement.
     */
    @State(Scope.Benchmark)
    public static class ValidState {
        String[] ibans;

        @Setup(Level.Trial)
        public void setup() {
            ibans = generateIbans(TARGET_SIZE);
            Log.info("Setup completed for valid IBAN data");
        }

    }

    /**
     * State containing invalid IBANs to observe exception overhead.
     */
    @State(Scope.Benchmark)
    public static class InvalidState {
        String[] ibans;

        @Setup(Level.Trial)
        public void setup() {
            ibans = generateIbans(TARGET_SIZE);
            for (int i = 0; i < ibans.length; i++) {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    // corrupt by swapping characters (e.g., to break checksum)
                    ibans[i] = swapRandomChars(ibans[i]);
                } else {
                    // Corrupt by removing up to 3 characters (e.g., to break length validation)
                    ibans[i] = removeRandomChars(ibans[i], 3);
                }
            }
            Log.info("Setup completed for invalid IBAN data");
        }
    }

    /**
     * Benchmarks the validation performance of the speedbanking iban-commons library.
     * Uses a pre-shuffled list of valid and invalid IBANs to measure real-world throughput.
     */
    @Benchmark
    @OperationsPerInvocation(TARGET_SIZE)
    public void bm1_IbanCommons_Valid(ValidState state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(de.speedbanking.iban.Iban.isValid(iban));
        }
    }

    /**
     * Benchmarks the validation performance of the {@code iban4j} library.
     */
    @Benchmark
    @OperationsPerInvocation(TARGET_SIZE)
    public void bm2_Iban4j_Valid(ValidState state, Blackhole bh) {
        for (String iban : state.ibans) {
            try {
                org.iban4j.IbanUtil.validate(iban);
                bh.consume(true);
            } catch (Exception ex) {
                bh.consume(false);
            }
        }
    }

    /**
     * Benchmarks the validation performance of the {@code Apache Commons Validator} library.
     * Uses regex-based IBAN validation via a singleton validator instance.
     */
    @Benchmark
    @OperationsPerInvocation(TARGET_SIZE)
    public void bm3_Apache_Valid(ValidState state, Blackhole bh) {
        org.apache.commons.validator.routines.IBANValidator validator
            = org.apache.commons.validator.routines.IBANValidator.getInstance();
        for (String iban : state.ibans) {
            bh.consume(validator.isValid(iban));
        }
    }

    /**
     * Benchmarks the validation performance of the {@code garvelink iban} library against valid IBANs.
     * Uses object-oriented parsing; exceptions are silently swallowed for invalid input.
     */
    @Benchmark
    @OperationsPerInvocation(TARGET_SIZE)
    public void bm4_Garvelink_Valid(ValidState state, Blackhole bh) {
        for (String iban : state.ibans) {
            try {
                bh.consume(nl.garvelink.iban.IBAN.parse(iban));
            } catch (Exception ignored) {
            }
        }
    }

    // --- Invalid-IBAN benchmarks ---
    // These measure real-world throughput including rejection cost (exception overhead,
    // length/checksum failures). Run together with the Valid benchmarks for a complete picture.

    /**
     * Benchmarks the rejection performance of the speedbanking iban-commons library against invalid IBANs.
     */
    @Benchmark
    @OperationsPerInvocation(TARGET_SIZE)
    public void bm5_IbanCommons_Invalid(InvalidState state, Blackhole bh) {
        for (String iban : state.ibans) {
            bh.consume(de.speedbanking.iban.Iban.isValid(iban));
        }
    }

    /**
     * Benchmarks the rejection performance of the {@code iban4j} library against invalid IBANs.
     */
    @Benchmark
    @OperationsPerInvocation(TARGET_SIZE)
    public void bm6_Iban4j_Invalid(InvalidState state, Blackhole bh) {
        for (String iban : state.ibans) {
            try {
                org.iban4j.IbanUtil.validate(iban);
                bh.consume(true);
            } catch (Exception ex) {
                bh.consume(false);
            }
        }
    }

    /**
     * Benchmarks the rejection performance of the {@code Apache Commons Validator} against invalid IBANs.
     */
    @Benchmark
    @OperationsPerInvocation(TARGET_SIZE)
    public void bm7_Apache_Invalid(InvalidState state, Blackhole bh) {
        org.apache.commons.validator.routines.IBANValidator validator
            = org.apache.commons.validator.routines.IBANValidator.getInstance();
        for (String iban : state.ibans) {
            bh.consume(validator.isValid(iban));
        }
    }

    /**
     * Benchmarks the rejection performance of the {@code garvelink iban} library against invalid IBANs.
     */
    @Benchmark
    @OperationsPerInvocation(TARGET_SIZE)
    public void bm8_Garvelink_Invalid(InvalidState state, Blackhole bh) {
        for (String iban : state.ibans) {
            try {
                bh.consume(nl.garvelink.iban.IBAN.parse(iban));
            } catch (Exception ignored) {
            }
        }
    }

    static String[] generateIbans(int targetSize) {
        List<IbanRegistry> countries = Arrays.asList(IbanRegistry.values());
        List<String> list = new ArrayList<>(targetSize);

        for (int i = 0; i < targetSize; i++) {
            // generate valid IBANs: 50% normalized, 50% formatted (with spaces)
            Iban iban = RandomIban.of(getRandomListEntry(countries));
            list.add(ThreadLocalRandom.current().nextBoolean()
                ? iban.toString()
                : iban.toFormattedString());
        }
        return list.toArray(new String[0]);
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

        int size = list.size();

        return list.get(ThreadLocalRandom.current().nextInt(size));
    }

    /**
     * Swaps two random, distinct characters in the input string to intentionally
     * corrupt a string such as an IBAN, making it invalid (e.g., failing a checksum validation).
     * <p>
     * Note: In rare edge cases (e.g., identical adjacent characters or a swap that happens
     * to preserve the checksum) the result may still pass validation. This is acceptable
     * for benchmark purposes, as the overall dataset will contain a realistic proportion
     * of invalid strings.
     *
     * @param input The string to corrupt. Must have a length of at least 2.
     * @return The corrupted string, or the original string if length &lt; 2 or input is {@code null}.
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
     * Corrupts an IBAN by removing characters at random positions.
     *
     * @param input          The base IBAN string.
     * @param maxCharsRemove The maximum number of characters to remove (must be >= 1).
     * @return The corrupted string.
     */
    static String removeRandomChars(String input, int maxCharsRemove) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder sb = new StringBuilder(input);
        int len = input.length();
        int charsToRemove = ThreadLocalRandom.current().nextInt(1, Math.min(len, maxCharsRemove + 1));

        for (int i = 0; i < charsToRemove; i++) {
            int index = ThreadLocalRandom.current().nextInt(sb.length());
            sb.deleteCharAt(index);
        }

        return sb.toString();
    }

    private static final class Log {
        static void info(String msg) {
            System.out.println("INFO: " + msg);
        }
    }

}
