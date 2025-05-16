package com.example.comparison;

import com.example.comparison.model.ComparisonBreak; // Ensure this points to your updated model
import com.example.comparison.service.GenericComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class GenericComparisonServiceListTest {

    private GenericComparisonService comparisonService;
    private List<TestDataObject> listA;
    private List<TestDataObject> listB;

    private static final String KEY_ATTRIBUTE = "indexKey";
    private static final List<String> ATTRIBUTES_TO_COMPARE = Arrays.asList("indexValue");

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
    }

    @BeforeEach
    void setUp() {
        comparisonService = new GenericComparisonService();
        listA = new ArrayList<>();
        listB = new ArrayList<>();

        for (int i = 1; i <= 100; i++) {
            listA.add(new TestDataObject(i, "value-" + i));
        }
        for (int i = 101; i <= 500; i++) {
            if (i % 2 != 0) {
                listA.add(new TestDataObject(i, "value-" + i));
            }
        }
        for (int i = 501; i <= 999; i++) {
            if (i % 2 == 0) {
                listA.add(new TestDataObject(i, "value-" + i));
            }
        }

        for (int i = 1; i <= 999; i++) {
            String value;
            if (i >= 500 && i <= 800) {
                value = "sweet-sport";
            } else {
                value = "value-" + i;
            }
            listB.add(new TestDataObject(i, value));
        }
    }

    @Test
    @DisplayName("Compare Lists with Specific Data Patterns and Assertions")
    void testCompareListsWithComplexData() {
        GenericComparisonService.ListComparisonResult<TestDataObject> result =
                comparisonService.compareLists(listA, listB, KEY_ATTRIBUTE, ATTRIBUTES_TO_COMPARE);

        assertNotNull(result, "Result should not be null");
        assertNotNull(result.breaks, "Breaks list should not be null");

        long expectedItemsProcessedA = 549;
        long expectedItemsProcessedB = 999;
        long expectedKeysOnlyInB = 450;
        long expectedKeysOnlyInA = 0;
        long expectedFullyMatchedKeys = 100 + 200 + 99; // 399
        long expectedKeysWithAttributeMismatch = 150;
        long expectedTotalAttributeDifferences = expectedKeysWithAttributeMismatch;

        System.out.println(result.toString());

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

        Optional<ComparisonBreak> onlyInBExample = findBreakByComparisonKeyAndType(result.breaks, "102", "onlyOnB");
        assertTrue(onlyInBExample.isPresent(), "Expected break for key 102 (onlyOnB) not found");
        assertEquals("RecordMissing", onlyInBExample.get().getDifferenceField(), "RecordMissing expected for differenceField");
        assertEquals("missing", onlyInBExample.get().getValueInCollectionA(), "Expected valueA for onlyOnB");
        assertEquals("exists", onlyInBExample.get().getValueInCollectionB(), "Expected valueB for onlyOnB");

        Optional<ComparisonBreak> matchedExample1 = findBreakByComparisonKeyAndType(result.breaks, "50", "match");
        assertTrue(matchedExample1.isPresent(), "Expected break for key 50 (match) not found");
        assertNull(matchedExample1.get().getDifferenceField(), "Difference field should be null for match type");

        Optional<ComparisonBreak> matchedExample2 = findBreakByComparisonKeyAndType(result.breaks, "101", "match");
        assertTrue(matchedExample2.isPresent(), "Expected break for key 101 (match) not found");

        Optional<ComparisonBreak> diffExample1 = findBreakForKeyAndAttribute(result.breaks, "502", "indexValue");
        assertTrue(diffExample1.isPresent(), "Expected break for key 502, attribute indexValue (difference) not found");
        assertEquals("difference", diffExample1.get().getBreakType(), "Type should be 'difference'");
        assertEquals("value-502", diffExample1.get().getValueInCollectionA());
        assertEquals("sweet-sport", diffExample1.get().getValueInCollectionB());

        Optional<ComparisonBreak> diffExample2 = findBreakForKeyAndAttribute(result.breaks, "800", "indexValue");
        assertTrue(diffExample2.isPresent(), "Expected break for key 800, attribute indexValue (difference) not found");
        assertEquals("difference", diffExample2.get().getBreakType());
        assertEquals("value-800", diffExample2.get().getValueInCollectionA());
        assertEquals("sweet-sport", diffExample2.get().getValueInCollectionB());

        Optional<ComparisonBreak> matchedExample3 = findBreakByComparisonKeyAndType(result.breaks, "802", "match");
        assertTrue(matchedExample3.isPresent(), "Expected break for key 802 (match) not found");

        Optional<ComparisonBreak> onlyInBExample2 = findBreakByComparisonKeyAndType(result.breaks, "999", "onlyOnB");
        assertTrue(onlyInBExample2.isPresent(), "Expected break for key 999 (onlyOnB) not found");
    }

    private Optional<ComparisonBreak> findBreakByComparisonKeyAndType(List<ComparisonBreak> breaks, String comparisonKey, String breakType) {
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