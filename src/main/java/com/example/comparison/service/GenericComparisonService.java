package com.example.comparison.service;

import com.example.comparison.model.ComparisonBreak; // Ensure this points to your updated model
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@Service
public class GenericComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(GenericComparisonService.class);

    @Autowired(required = false) // Make MongoTemplate optional for non-Spring unit tests
    private MongoTemplate mongoTemplate;

    // Setter for MongoTemplate to allow injection in tests if needed, or manual setup
    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }


    // Helper class to store results from list comparison
    public static class ListComparisonResult<T> {
        public final List<ComparisonBreak> breaks;
        public final long itemsProcessedA;
        public final long itemsProcessedB;
        public final long keysOnlyInA;
        public final long keysOnlyInB;
        public final long keysWithAttributeMismatch;
        public final long fullyMatchedKeys;
        public final long totalAttributeDifferences;

        public ListComparisonResult(List<ComparisonBreak> breaks, long itemsProcessedA, long itemsProcessedB,
                                    long keysOnlyInA, long keysOnlyInB, long keysWithAttributeMismatch,
                                    long fullyMatchedKeys, long totalAttributeDifferences) {
            this.breaks = breaks;
            this.itemsProcessedA = itemsProcessedA;
            this.itemsProcessedB = itemsProcessedB;
            this.keysOnlyInA = keysOnlyInA;
            this.keysOnlyInB = keysOnlyInB;
            this.keysWithAttributeMismatch = keysWithAttributeMismatch;
            this.fullyMatchedKeys = fullyMatchedKeys;
            this.totalAttributeDifferences = totalAttributeDifferences;
        }

        @Override
        public String toString() {
            long commonKeys = fullyMatchedKeys + keysWithAttributeMismatch;
            return String.format(
                    "List Comparison Summary:\n" +
                            "  Items Processed from List A: %d\n" +
                            "  Items Processed from List B: %d\n" +
                            "  Keys Only in List A: %d\n" +
                            "  Keys Only in List B: %d\n" +
                            "  Common Keys Found: %d\n" +
                            "    - Fully Matched Keys: %d\n" +
                            "    - Keys with Attribute Mismatches: %d\n" +
                            "  Total Individual Attribute Differences: %d\n" +
                            "  Total ComparisonBreak Records Generated: %d",
                    itemsProcessedA, itemsProcessedB, keysOnlyInA, keysOnlyInB,
                    commonKeys,
                    fullyMatchedKeys, keysWithAttributeMismatch,
                    totalAttributeDifferences,
                    breaks.size()
            );
        }
    }


    public <T> void compareCollections(Class<T> clazz,
                                       String collectionA,
                                       String collectionB,
                                       String keyAttribute,
                                       List<String> attributesToCompare,
                                       String outputCollectionName) {
        if (mongoTemplate == null) {
            throw new IllegalStateException("MongoTemplate has not been initialized. Call setMongoTemplate or ensure Spring context is loaded.");
        }

        long itemsProcessedA = 0;
        long itemsProcessedB = 0;
        long keysOnlyInA = 0;
        long keysOnlyInB = 0;
        long keysWithAttributeMismatch = 0;
        long fullyMatchedKeys = 0;
        long totalAttributeDifferences = 0;

        Query query = new Query().with(Sort.by(Sort.Direction.ASC, keyAttribute));
        List<ComparisonBreak> allBreaksAndMatches = new ArrayList<>();

        try (CloseableIterator<T> streamA = mongoTemplate.stream(query, clazz, collectionA);
             CloseableIterator<T> streamB = mongoTemplate.stream(query, clazz, collectionB)) {

            Iterator<T> iteratorA = streamA;
            Iterator<T> iteratorB = streamB;

            T currentA = null;
            if (iteratorA.hasNext()) {
                currentA = iteratorA.next();
                itemsProcessedA++;
            }
            T currentB = null;
            if (iteratorB.hasNext()) {
                currentB = iteratorB.next();
                itemsProcessedB++;
            }

            while (currentA != null || currentB != null) {
                if (currentA != null && currentB != null) {
                    Comparable<?> keyA = getKeyValue(currentA, keyAttribute, collectionA);
                    Comparable<?> keyB = getKeyValue(currentB, keyAttribute, collectionB);

                    int cmp = compareKeys(keyA, keyB, keyAttribute);

                    String keyAStr = (keyA == null) ? "null" : keyA.toString();
                    String keyBStr = (keyB == null) ? "null" : keyB.toString();

                    if (cmp == 0) {
                        int individualDiffsForKey = recordAttributeDifferences(currentA, currentB, keyAStr, attributesToCompare, allBreaksAndMatches);
                        if (individualDiffsForKey == 0) {
                            fullyMatchedKeys++;
                            // For a "match", differenceField, valueA, valueB are null.
                            allBreaksAndMatches.add(new ComparisonBreak(keyAStr, null, null, null, "match"));
                        } else {
                            keysWithAttributeMismatch++;
                            totalAttributeDifferences += individualDiffsForKey;
                        }
                        currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                        if (currentA != null) itemsProcessedA++;
                        currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                        if (currentB != null) itemsProcessedB++;
                    } else if (cmp < 0) {
                        keysOnlyInA++;
                        // differenceField="RecordMissing", valueA="exists", valueB="missing"
                        allBreaksAndMatches.add(new ComparisonBreak(keyAStr, "RecordMissing", "exists", "missing", "onlyOnA"));
                        currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                        if (currentA != null) itemsProcessedA++;
                    } else { // cmp > 0
                        keysOnlyInB++;
                        // differenceField="RecordMissing", valueA="missing", valueB="exists"
                        allBreaksAndMatches.add(new ComparisonBreak(keyBStr, "RecordMissing", "missing", "exists", "onlyOnB"));
                        currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                        if (currentB != null) itemsProcessedB++;
                    }
                } else if (currentA != null) {
                    keysOnlyInA++;
                    Comparable<?> keyA = getKeyValue(currentA, keyAttribute, collectionA);
                    String keyAStr = (keyA == null) ? "null" : keyA.toString();
                    allBreaksAndMatches.add(new ComparisonBreak(keyAStr, "RecordMissing", "exists", "missing", "onlyOnA"));
                    currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                    if (currentA != null) itemsProcessedA++;
                } else { // currentB must be non-null
                    keysOnlyInB++;
                    Comparable<?> keyB = getKeyValue(currentB, keyAttribute, collectionB);
                    String keyBStr = (keyB == null) ? "null" : keyB.toString();
                    allBreaksAndMatches.add(new ComparisonBreak(keyBStr, "RecordMissing", "missing", "exists", "onlyOnB"));
                    currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                    if (currentB != null) itemsProcessedB++;
                }
            }

            if (!allBreaksAndMatches.isEmpty()) {
                mongoTemplate.insert(allBreaksAndMatches, outputCollectionName);
                logger.info("Comparison results for collections '{}' and '{}' (key: '{}') stored in '{}'.",
                        collectionA, collectionB, keyAttribute, outputCollectionName);
            } else {
                logger.info("Comparison for collections '{}' and '{}' (key: '{}'): No differences, unique items, or matches found to report to collection '{}'.",
                        collectionA, collectionB, keyAttribute, outputCollectionName);
            }

        } catch (Exception e) {
            logger.error("Error during MongoDB collection comparison between {} and {}: {}", collectionA, collectionB, e.getMessage(), e);
            throw new RuntimeException("Failed to compare MongoDB collections " + collectionA + " and " + collectionB, e);
        }

        logSummary("MongoDB Collection Comparison", collectionA, collectionB, keyAttribute,
                itemsProcessedA, itemsProcessedB, keysOnlyInA, keysOnlyInB,
                keysWithAttributeMismatch, fullyMatchedKeys, totalAttributeDifferences,
                allBreaksAndMatches.size(), outputCollectionName);
    }

    public <T> ListComparisonResult<T> compareLists(List<T> listA, List<T> listB,
                                                    String keyAttribute,
                                                    List<String> attributesToCompare) {
        long itemsProcessedA = 0;
        long itemsProcessedB = 0;
        long keysOnlyInA = 0;
        long keysOnlyInB = 0;
        long keysWithAttributeMismatch = 0;
        long fullyMatchedKeys = 0;
        long totalAttributeDifferences = 0;
        List<ComparisonBreak> allBreaksAndMatches = new ArrayList<>();

        Comparator<T> keyComparator = (o1, o2) -> {
            if (o1 == null && o2 == null) return 0;
            if (o1 == null) return -1;
            if (o2 == null) return 1;

            Comparable<?> key1 = getKeyValue(o1, keyAttribute, "listA_internal_sort");
            Comparable<?> key2 = getKeyValue(o2, keyAttribute, "listB_internal_sort");
            return compareKeys(key1, key2, keyAttribute);
        };

        List<T> sortedA = new ArrayList<>(listA);
        List<T> sortedB = new ArrayList<>(listB);
        try {
            sortedA.sort(keyComparator);
            sortedB.sort(keyComparator);
        } catch (IllegalArgumentException e) {
            logger.error("Error during list pre-sort for key attribute '{}': {}. Ensure key attribute is Comparable.", keyAttribute, e.getMessage(), e);
            throw new RuntimeException("Failed to sort lists for comparison due to non-Comparable key: " + keyAttribute, e);
        }


        Iterator<T> iteratorA = sortedA.iterator();
        Iterator<T> iteratorB = sortedB.iterator();

        T currentA = null;
        if (iteratorA.hasNext()) {
            currentA = iteratorA.next();
            itemsProcessedA++;
        }
        T currentB = null;
        if (iteratorB.hasNext()) {
            currentB = iteratorB.next();
            itemsProcessedB++;
        }

        try {
            while (currentA != null || currentB != null) {
                if (currentA != null && currentB != null) {
                    Comparable<?> keyA = getKeyValue(currentA, keyAttribute, "listA");
                    Comparable<?> keyB = getKeyValue(currentB, keyAttribute, "listB");

                    int cmp = compareKeys(keyA, keyB, keyAttribute);

                    String keyAStr = (keyA == null) ? "null" : keyA.toString();
                    String keyBStr = (keyB == null) ? "null" : keyB.toString();

                    if (cmp == 0) {
                        int individualDiffsForKey = recordAttributeDifferences(currentA, currentB, keyAStr, attributesToCompare, allBreaksAndMatches);
                        if (individualDiffsForKey == 0) {
                            fullyMatchedKeys++;
                            allBreaksAndMatches.add(new ComparisonBreak(keyAStr, null, null, null, "match"));
                        } else {
                            keysWithAttributeMismatch++;
                            totalAttributeDifferences += individualDiffsForKey;
                        }
                        currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                        if (currentA != null) itemsProcessedA++;
                        currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                        if (currentB != null) itemsProcessedB++;
                    } else if (cmp < 0) {
                        keysOnlyInA++;
                        allBreaksAndMatches.add(new ComparisonBreak(keyAStr, "RecordMissing", "exists", "missing", "onlyOnA"));
                        currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                        if (currentA != null) itemsProcessedA++;
                    } else { // cmp > 0
                        keysOnlyInB++;
                        allBreaksAndMatches.add(new ComparisonBreak(keyBStr, "RecordMissing", "missing", "exists", "onlyOnB"));
                        currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                        if (currentB != null) itemsProcessedB++;
                    }
                } else if (currentA != null) {
                    keysOnlyInA++;
                    Comparable<?> keyA = getKeyValue(currentA, keyAttribute, "listA");
                    String keyAStr = (keyA == null) ? "null" : keyA.toString();
                    allBreaksAndMatches.add(new ComparisonBreak(keyAStr, "RecordMissing", "exists", "missing", "onlyOnA"));
                    currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                    if (currentA != null) itemsProcessedA++;
                } else { // currentB must be non-null
                    keysOnlyInB++;
                    Comparable<?> keyB = getKeyValue(currentB, keyAttribute, "listB");
                    String keyBStr = (keyB == null) ? "null" : keyB.toString();
                    allBreaksAndMatches.add(new ComparisonBreak(keyBStr, "RecordMissing", "missing", "exists", "onlyOnB"));
                    currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                    if (currentB != null) itemsProcessedB++;
                }
            }
        } catch (Exception e) {
            logger.error("Error during Java list comparison (key: {}): {}", keyAttribute, e.getMessage(), e);
            throw new RuntimeException("Failed to compare lists with key attribute " + keyAttribute, e);
        }

        ListComparisonResult<T> result = new ListComparisonResult<>(
                allBreaksAndMatches, itemsProcessedA, itemsProcessedB, keysOnlyInA, keysOnlyInB,
                keysWithAttributeMismatch, fullyMatchedKeys, totalAttributeDifferences
        );

        logSummary("Java List Comparison", "List A", "List B", keyAttribute,
                itemsProcessedA, itemsProcessedB, keysOnlyInA, keysOnlyInB,
                keysWithAttributeMismatch, fullyMatchedKeys, totalAttributeDifferences,
                allBreaksAndMatches.size(), null);

        return result;
    }

    private int compareKeys(Comparable<?> keyA, Comparable<?> keyB, String keyAttribute) {
        if (keyA == null && keyB == null) {
            return 0;
        } else if (keyA == null) {
            return -1;
        } else if (keyB == null) {
            return 1;
        } else {
            try {
                //noinspection unchecked,rawtypes
                return ((Comparable)keyA).compareTo(keyB);
            } catch (ClassCastException e) {
                logger.warn("ClassCastException during key comparison for key attribute '{}'. " +
                                "Key A: '{}' (type {}), Key B: '{}' (type {}). " +
                                "Falling back to String comparison.",
                        keyAttribute, keyA, keyA.getClass().getName(), keyB, keyB.getClass().getName(), e);
                return String.valueOf(keyA).compareTo(String.valueOf(keyB));
            }
        }
    }

    private <T> Comparable<?> getKeyValue(T object, String keyAttribute, String sourceHint) {
        if (object == null) {
            logger.warn("Encountered a null object from source '{}' while trying to get key attribute '{}'. Treating key as null.", sourceHint, keyAttribute);
            return null;
        }
        BeanWrapper wrapper = new BeanWrapperImpl(object);
        Object keyValue;
        try {
            keyValue = wrapper.getPropertyValue(keyAttribute);
        } catch (NotReadablePropertyException e) {
            logger.trace("Key attribute '{}' not found on an object from source '{}'. Treating key as null. Object: {}", keyAttribute, sourceHint, object, e);
            return null;
        }

        if (keyValue == null) {
            return null;
        }

        if (keyValue instanceof Comparable) {
            return (Comparable<?>) keyValue;
        }

        String errorMessage = String.format(
                "Key attribute '%s' from source '%s' yielded a non-null value of type '%s' which is not Comparable. Value: '%s'. Object: %s",
                keyAttribute, sourceHint, keyValue.getClass().getName(), keyValue.toString(), object.toString()
        );
        logger.error(errorMessage);
        throw new IllegalArgumentException(errorMessage);
    }

    private <T> int recordAttributeDifferences(T a,
                                               T b,
                                               String comparisonKey, // Renamed from 'key' to match model conceptually
                                               List<String> attributesToCompare,
                                               List<ComparisonBreak> differencesOutputList) {
        BeanWrapper wrapperA = new BeanWrapperImpl(a);
        BeanWrapper wrapperB = new BeanWrapperImpl(b);
        int currentKeyDifferences = 0;

        for (String attr : attributesToCompare) {
            Object valueAObj = null;
            boolean attrAMissing = false;
            try {
                valueAObj = wrapperA.getPropertyValue(attr);
            } catch (NotReadablePropertyException e) {
                attrAMissing = true;
                logger.trace("Attribute '{}' not readable from object in source A for key '{}'. Assuming null for comparison.", attr, comparisonKey);
            }

            Object valueBObj = null;
            boolean attrBMissing = false;
            try {
                valueBObj = wrapperB.getPropertyValue(attr);
            } catch (NotReadablePropertyException e) {
                attrBMissing = true;
                logger.trace("Attribute '{}' not readable from object in source B for key '{}'. Assuming null for comparison.", attr, comparisonKey);
            }

            if (!Objects.equals(valueAObj, valueBObj)) {
                String valueInCollectionA = attrAMissing ? "[[missing]]" : (valueAObj == null ? "null" : valueAObj.toString());
                String valueInCollectionB = attrBMissing ? "[[missing]]" : (valueBObj == null ? "null" : valueBObj.toString());
                String differenceField = attr; // The attribute name that differs

                differencesOutputList.add(new ComparisonBreak(
                        comparisonKey,
                        differenceField,
                        valueInCollectionA,
                        valueInCollectionB,
                        "difference" // breakType
                ));
                currentKeyDifferences++;
            }
        }
        return currentKeyDifferences;
    }

    private void logSummary(String comparisonTitle, String sourceAName, String sourceBName, String keyAttribute,
                            long itemsProcessedA, long itemsProcessedB, long keysOnlyInA, long keysOnlyInB,
                            long keysWithAttributeMismatch, long fullyMatchedKeys, long totalAttributeDifferences,
                            long totalBreaksWritten, String outputTargetName) {

        long commonKeys = fullyMatchedKeys + keysWithAttributeMismatch;
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("%s Summary (Key: '%s'):\n", comparisonTitle, keyAttribute));
        summary.append(String.format("  Source A ('%s') Items Processed: %d\n", sourceAName, itemsProcessedA));
        summary.append(String.format("  Source B ('%s') Items Processed: %d\n", sourceBName, itemsProcessedB));
        summary.append(String.format("  Keys Only in A: %d\n", keysOnlyInA));
        summary.append(String.format("  Keys Only in B: %d\n", keysOnlyInB));
        summary.append(String.format("  Common Keys Found: %d\n", commonKeys));
        summary.append(String.format("    - Fully Matched Keys: %d\n", fullyMatchedKeys));
        summary.append(String.format("    - Keys with Attribute Mismatches: %d\n", keysWithAttributeMismatch));
        summary.append(String.format("  Total Individual Attribute Differences: %d\n", totalAttributeDifferences));
        if (outputTargetName != null) {
            summary.append(String.format("  Total Records Written to '%s': %d", outputTargetName, totalBreaksWritten));
        } else {
            summary.append(String.format("  Total ComparisonBreak Records Generated: %d", totalBreaksWritten));
        }
        logger.info(summary.toString());
    }
}