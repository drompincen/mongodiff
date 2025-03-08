package com.example.comparison.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class MyEntity {
    @Id
    private String id;
    private String comparisonKey; // Key used for merging/sorting
    // Only a few of the many fields we want to compare:
    private String field1;
    private String field2;
    private String field3;
    // ... imagine up to 80 fields, with only a subset used for comparison

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getComparisonKey() { return comparisonKey; }
    public void setComparisonKey(String comparisonKey) { this.comparisonKey = comparisonKey; }

    public String getField1() { return field1; }
    public void setField1(String field1) { this.field1 = field1; }

    public String getField2() { return field2; }
    public void setField2(String field2) { this.field2 = field2; }

    public String getField3() { return field3; }
    public void setField3(String field3) { this.field3 = field3; }
}
