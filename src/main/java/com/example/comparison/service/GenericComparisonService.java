package com.example.comparison.service;

import com.example.comparison.model.ComparisonBreak;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@Service
public class GenericComparisonService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Compares two MongoDB collections of objects of type T.
     *
     * @param clazz                the class type (e.g. Account.class, Car.class)
     * @param collectionA          name of the first collection
     * @param collectionB          name of the second collection
     * @param keyAttribute         the property name used as the key for sorting and matching (must be Comparable)
     * @param attributesToCompare  list of property names to compare between matching objects
     * @param outputCollectionName name of the collection where differences will be stored
     * @param <T>                  type of the objects to compare
     */
    public <T> void compareCollections(Class<T> clazz,
                                       String collectionA,
                                       String collectionB,
                                       String keyAttribute,
                                       List<String> attributesToCompare,
                                       String outputCollectionName) {
        Query query = new Query().with(Sort.by(keyAttribute));
        try (CloseableIterator<T> streamA = mongoTemplate.stream(query, clazz, collectionA);
             CloseableIterator<T> streamB = mongoTemplate.stream(query, clazz, collectionB)) {

            Iterator<T> iteratorA = streamA;
            Iterator<T> iteratorB = streamB;

            T currentA = iteratorA.hasNext() ? iteratorA.next() : null;
            T currentB = iteratorB.hasNext() ? iteratorB.next() : null;

            List<ComparisonBreak> differences = new ArrayList<>();

            while (currentA != null || currentB != null) {
                if (currentA != null && currentB != null) {
                    Comparable keyA = getKeyValue(currentA, keyAttribute);
                    Comparable keyB = getKeyValue(currentB, keyAttribute);
                    int cmp = keyA.compareTo(keyB);
                    if (cmp == 0) {
                        compareAndRecordDifferences(currentA, currentB, keyA.toString(), attributesToCompare, differences);
                        currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                        currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                    } else if (cmp < 0) {
                        // Record exists in collectionA but is missing in collectionB
                        differences.add(new ComparisonBreak(keyA.toString(), "RecordMissing", "exists", "missing", "onlyOnA"));
                        currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                    } else {
                        // Record exists in collectionB but is missing in collectionA
                        differences.add(new ComparisonBreak(keyB.toString(), "RecordMissing", "missing", "exists", "onlyOnB"));
                        currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                    }
                } else if (currentA != null) {
                    Comparable keyA = getKeyValue(currentA, keyAttribute);
                    differences.add(new ComparisonBreak(keyA.toString(), "RecordMissing", "exists", "missing", "onlyOnA"));
                    currentA = iteratorA.hasNext() ? iteratorA.next() : null;
                } else if (currentB != null) {
                    Comparable keyB = getKeyValue(currentB, keyAttribute);
                    differences.add(new ComparisonBreak(keyB.toString(), "RecordMissing", "missing", "exists", "onlyOnB"));
                    currentB = iteratorB.hasNext() ? iteratorB.next() : null;
                }
            }

            if (!differences.isEmpty()) {
                mongoTemplate.insert(differences, outputCollectionName);
            }
        }
    }

    private <T> Comparable getKeyValue(T object, String keyAttribute) {
        BeanWrapper wrapper = new BeanWrapperImpl(object);
        Object keyValue = wrapper.getPropertyValue(keyAttribute);
        if (keyValue instanceof Comparable) {
            return (Comparable) keyValue;
        }
        throw new IllegalArgumentException("Key attribute " + keyAttribute + " is not Comparable");
    }

    private <T> void compareAndRecordDifferences(T a,
                                                 T b,
                                                 String key,
                                                 List<String> attributesToCompare,
                                                 List<ComparisonBreak> differences) {
        BeanWrapper wrapperA = new BeanWrapperImpl(a);
        BeanWrapper wrapperB = new BeanWrapperImpl(b);
        for (String attr : attributesToCompare) {
            Object valueA = wrapperA.getPropertyValue(attr);
            Object valueB = wrapperB.getPropertyValue(attr);
            if (!Objects.equals(valueA, valueB)) {
                differences.add(new ComparisonBreak(
                        key,
                        attr,
                        valueA == null ? "null" : valueA.toString(),
                        valueB == null ? "null" : valueB.toString(),
                        "difference"
                ));
            }
        }
    }
}
