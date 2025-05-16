package com.example.comparison;

import com.example.comparison.model.ComparisonBreak;
import com.example.comparison.service.GenericComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class GenericComparisonServiceLargeScaleTest {

    private static final Logger logger = LoggerFactory.getLogger(GenericComparisonServiceLargeScaleTest.class);

    private GenericComparisonService comparisonService;
    private List<TestDataObject> listA;
    private List<TestDataObject> listB;

    private static final String KEY_ATTRIBUTE = "indexKey";
    private static final List<String> ATTRIBUTES_TO_COMPARE = Arrays.asList("indexValue");
    private static final int TOTAL_ITEMS_RANGE = 200_000;

    static class TestDataObject {
        private final int indexKey;
        private final String indexValue;

        public TestDataObject(int indexKey, String indexValue) {
            this.indexKey = indexKey;
            this.indexValue = indexValue;
        }

        public int getIndexKey() {
            return indexKey;
        }

        public String getIndexValue() {
            return indexValue;
        }

        @Override
        public String toString() {
            return "TestDataObject{" +
                    "indexKey=" + indexKey +
                    ", indexValue='" + indexValue + '\'' +
                    '}';
        }

        // For Set operations if needed
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestDataObject that = (TestDataObject) o;
            return indexKey == that.indexKey;
        }

        @Override
        public int hashCode() {
            return Objects.hash(indexKey);
        }
    }

    @BeforeEach
    void setUp() {
        comparisonService = new GenericComparisonService();
        listA = new ArrayList<>();
        listB = new ArrayList<>();
        Random random = new Random(42); // Seed for reproducibility

        logger.info("Setting up large scale test data...");

        // --- Populate Collection A ---
        Set<Integer> aKeys = new HashSet<>();
        Set<Integer> aMissingKeys = new HashSet<>();

        // Range 1: 10k - 70k (exclusive of 70k for range definition, keys 10000 to 69999)
        // Approximately 20% missing from this range of 60k items = 12k missing
        int missingCountRange1 = (70_000 - 10_000) / 5;
        while (aMissingKeys.size() < missingCountRange1) {
            aMissingKeys.add(10_000 + random.nextInt(70_000 - 10_000));
        }

        // Range 2: 150k - 170k (exclusive of 170k for range definition, keys 150000 to 169999)
        // Approximately 20% missing from this range of 20k items = 4k missing
        int missingCountRange2Target = aMissingKeys.size() + (170_000 - 150_000) / 5;
        while (aMissingKeys.size() < missingCountRange2Target) {
            int key = 150_000 + random.nextInt(170_000 - 150_000);
            aMissingKeys.add(key); // HashSet handles duplicates
        }

        for (int i = 1; i <= TOTAL_ITEMS_RANGE; i++) {
            if (!aMissingKeys.contains(i)) {
                listA.add(new TestDataObject(i, "valueA-" + i));
                aKeys.add(i);
            }
        }
        logger.info("List A populated with {} items. Expected missing: {}.", listA.size(), aMissingKeys.size());


        // --- Populate Collection B ---
        // Missing first 50k (1 to 50000) and last 50k (150001 to 200000)
        Set<Integer> bKeys = new HashSet<>();
        for (int i = 1; i <= TOTAL_ITEMS_RANGE; i++) {
            if (i > 50_000 && i <= (TOTAL_ITEMS_RANGE - 50_000)) { // Items from 50001 to 150000
                // Introduce some value differences in B for common keys for more comprehensive testing
                String valueB;
                if (i >= 60_000 && i < 65_000) { // Example range for value difference
                    valueB = "valueB-MODIFIED-" + i;
                } else {
                    valueB = "valueA-" + i; // Make most values same as A for matches
                }
                listB.add(new TestDataObject(i, valueB));
                bKeys.add(i);
            }
        }
        logger.info("List B populated with {} items.", listB.size());

        // Shuffle lists to ensure the sort in compareLists is effective
        Collections.shuffle(listA, random);
        Collections.shuffle(listB, random);

        logger.info("Test data setup complete.");
    }

    @Test
    @DisplayName("Compare Large Lists (200k) with Specific Missing Patterns and Value Diffs")
    void testCompareLargeLists() {
        logger.info("Starting large list comparison...");
        long startTime = System.currentTimeMillis();

        GenericComparisonService.ListComparisonResult<TestDataObject> result =
                comparisonService.compareLists(listA, listB, KEY_ATTRIBUTE, ATTRIBUTES_TO_COMPARE);

        long endTime = System.currentTimeMillis();
        logger.info("Large list comparison finished in {} ms.", (endTime - startTime));

        assertNotNull(result, "Result should not be null");
        assertNotNull(result.breaks, "Breaks list should not be null");

        logger.info("Calculating expected counts for assertions...");

        // --- Calculate Expected Counts (more robustly using Sets) ---
        Set<Integer> keysInA = listA.stream().map(TestDataObject::getIndexKey).collect(Collectors.toSet());
        Set<Integer> keysInB = listB.stream().map(TestDataObject::getIndexKey).collect(Collectors.toSet());

        Set<Integer> commonKeys = new HashSet<>(keysInA);
        commonKeys.retainAll(keysInB);

        Set<Integer> onlyInAKeys = new HashSet<>(keysInA);
        onlyInAKeys.removeAll(keysInB);

        Set<Integer> onlyInBKeys = new HashSet<>(keysInB);
        onlyInBKeys.removeAll(keysInA);

        long expectedItemsProcessedA = listA.size();
        long expectedItemsProcessedB = listB.size();
        long expectedKeysOnlyInA = onlyInAKeys.size();
        long expectedKeysOnlyInB = onlyInBKeys.size();

        long expectedFullyMatchedKeys = 0;
        long expectedKeysWithAttributeMismatch = 0;

        for (Integer commonKey : commonKeys) {
            // Since values in B are mostly "valueA-" + i, except for the modified range
            String valueAForCommon = "valueA-" + commonKey;
            String valueBForCommon;
            if (commonKey >= 60_000 && commonKey < 65_000) {
                valueBForCommon = "valueB-MODIFIED-" + commonKey;
            } else {
                valueBForCommon = "valueA-" + commonKey;
            }

            if (valueAForCommon.equals(valueBForCommon)) {
                expectedFullyMatchedKeys++;
            } else {
                expectedKeysWithAttributeMismatch++;
            }
        }

        long expectedTotalAttributeDifferences = expectedKeysWithAttributeMismatch; // Only one attribute compared

        logger.info("Expected Summary:\n  Processed A: {}\n  Processed B: {}\n  Only A: {}\n  Only B: {}\n  Matched: {}\n  Mismatched Attrs: {}\n  Total Attr Diffs: {}",
                expectedItemsProcessedA, expectedItemsProcessedB, expectedKeysOnlyInA, expectedKeysOnlyInB,
                expectedFullyMatchedKeys, expectedKeysWithAttributeMismatch, expectedTotalAttributeDifferences);

        // --- Assertions on Summary Statistics ---
        logger.info("Actual Result Summary:\n{}", result.toString()); // Print summary for easy debugging

        assertEquals(expectedItemsProcessedA, result.itemsProcessedA, "Mismatch in items processed from A");
        assertEquals(expectedItemsProcessedB, result.itemsProcessedB, "Mismatch in items processed from B");
        assertEquals(expectedKeysOnlyInA, result.keysOnlyInA, "Mismatch in keys only in A");
        assertEquals(expectedKeysOnlyInB, result.keysOnlyInB, "Mismatch in keys only in B");
        assertEquals(expectedFullyMatchedKeys, result.fullyMatchedKeys, "Mismatch in fully matched keys");
        assertEquals(expectedKeysWithAttributeMismatch, result.keysWithAttributeMismatch, "Mismatch in keys with attribute mismatches");
        assertEquals(expectedTotalAttributeDifferences, result.totalAttributeDifferences, "Mismatch in total attribute differences");

        long expectedTotalBreaks = expectedKeysOnlyInA + expectedKeysOnlyInB +
                expectedFullyMatchedKeys + expectedKeysWithAttributeMismatch;
        assertEquals(expectedTotalBreaks, result.breaks.size(), "Mismatch in total number of break records");
        logger.info("Summary statistics assertions passed.");

        // --- Optional: Assertions on Specific Break Examples (can be slow for very large lists if not careful) ---
        // For large lists, checking presence of a *few* known examples is usually sufficient.
        // Avoid iterating through result.breaks many times.

        // Example: An item only in A (e.g., key 1, which B is missing)
        if (!onlyInAKeys.isEmpty()) {
            Integer sampleOnlyInAKey = onlyInAKeys.iterator().next();
            Optional<ComparisonBreak> onlyInAExample = findBreakByComparisonKeyAndType(result.breaks, String.valueOf(sampleOnlyInAKey), "onlyOnA");
            assertTrue(onlyInAExample.isPresent(), "Expected break for a key only in A (e.g., " + sampleOnlyInAKey + ") not found");
            if (onlyInAExample.isPresent()) {
                assertEquals("RecordMissing", onlyInAExample.get().getDifferenceField());
            }
        }


        // Example: An item only in B (e.g., key 75000, A might be missing it randomly, B should have it)
        // B has 50001 to 150000. A might miss items in 10k-70k.
        // So, a key like 50001 (if not missed by A) should be common.
        // A key like 75000 if A *is* missing it, would be onlyOnB.
        // Let's find a key known to be in B but verify against A's actual content.
        if (!onlyInBKeys.isEmpty()) {
            Integer sampleOnlyInBKey = onlyInBKeys.stream().filter(k -> k > 70000 && k < 150000).findFirst().orElse(onlyInBKeys.iterator().next());
            Optional<ComparisonBreak> onlyInBExample = findBreakByComparisonKeyAndType(result.breaks, String.valueOf(sampleOnlyInBKey), "onlyOnB");
            assertTrue(onlyInBExample.isPresent(), "Expected break for a key only in B (e.g., " + sampleOnlyInBKey + ") not found");
            if (onlyInBExample.isPresent()) {
                assertEquals("RecordMissing", onlyInBExample.get().getDifferenceField());
            }
        }


        // Example: A matched item (e.g., key 55000, outside B's modified range)
        if (commonKeys.contains(55000) && (55000 < 60000 || 55000 >= 65000) ) { // ensure it's a match candidate
            Optional<ComparisonBreak> matchedExample = findBreakByComparisonKeyAndType(result.breaks, "55000", "match");
            if (keysInA.contains(55000) && keysInB.contains(55000) && !(55000 >= 60000 && 55000 < 65000)){ //if it's truly a match
                assertTrue(matchedExample.isPresent(), "Expected break for key 55000 (match) not found");
                if(matchedExample.isPresent()) assertNull(matchedExample.get().getDifferenceField());
            } else if (matchedExample.isPresent()) {
                assertFalse(true, "Key 55000 was expected to be a match but found as: " + matchedExample.get().getBreakType());
            }
        }


        // Example: An attribute difference (e.g., key 60000, within B's modified range)
        if (commonKeys.contains(60000) && (60000 >= 60000 && 60000 < 65000)) { // ensure it's a diff candidate
            Optional<ComparisonBreak> diffExample = findBreakForKeyAndAttribute(result.breaks, "60000", "indexValue");
            assertTrue(diffExample.isPresent(), "Expected break for key 60000, attribute indexValue (difference) not found");
            if (diffExample.isPresent()) {
                assertEquals("difference", diffExample.get().getBreakType());
                assertEquals("valueA-60000", diffExample.get().getValueInCollectionA());
                assertEquals("valueB-MODIFIED-60000", diffExample.get().getValueInCollectionB());
            }
        }
        logger.info("Specific break assertions passed (if conditions met).");
    }

    private Optional<ComparisonBreak> findBreakByComparisonKeyAndType(List<ComparisonBreak> breaks, String comparisonKey, String breakType) {
        // For large lists, this linear scan can be slow if called many times.
        // If performance is critical for many such checks, consider indexing `breaks` by key and type.
        return breaks.stream()
                .filter(b -> b.getComparisonKey().equals(comparisonKey) && b.getBreakType().equals(breakType))
                .findFirst();
    }

    private Optional<ComparisonBreak> findBreakForKeyAndAttribute(List<ComparisonBreak> breaks, String comparisonKey, String differenceField) {
        return breaks.stream()
                .filter(b -> b.getComparisonKey().equals(comparisonKey) &&
                        b.getBreakType().equals("difference") &&
                        Objects.equals(b.getDifferenceField(), differenceField))
                .findFirst();
    }
}