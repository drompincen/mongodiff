package com.example.comparison.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "accountComparisonBreaks")
public class ComparisonBreak {
    @Id
    private String id;
    private String comparisonKey;
    private String differenceField;
    private String valueInCollectionA;
    private String valueInCollectionB;
    private String breakType; // "difference", "onlyOnA", "onlyOnB"

    public ComparisonBreak() {}

    public ComparisonBreak(String comparisonKey, String differenceField, String valueInCollectionA, String valueInCollectionB, String breakType) {
        this.comparisonKey = comparisonKey;
        this.differenceField = differenceField;
        this.valueInCollectionA = valueInCollectionA;
        this.valueInCollectionB = valueInCollectionB;
        this.breakType = breakType;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getComparisonKey() { return comparisonKey; }
    public void setComparisonKey(String comparisonKey) { this.comparisonKey = comparisonKey; }

    public String getDifferenceField() { return differenceField; }
    public void setDifferenceField(String differenceField) { this.differenceField = differenceField; }

    public String getValueInCollectionA() { return valueInCollectionA; }
    public void setValueInCollectionA(String valueInCollectionA) { this.valueInCollectionA = valueInCollectionA; }

    public String getValueInCollectionB() { return valueInCollectionB; }
    public void setValueInCollectionB(String valueInCollectionB) { this.valueInCollectionB = valueInCollectionB; }

    public String getBreakType() { return breakType; }
    public void setBreakType(String breakType) { this.breakType = breakType; }
}
