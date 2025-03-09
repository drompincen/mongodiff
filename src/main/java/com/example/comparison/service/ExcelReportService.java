package com.example.comparison.service;

import com.example.comparison.model.ComparisonBreak;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelReportService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Generates an Excel workbook containing:
     * - A "Summary" sheet with counts per break type.
     * - "onlyOnA" and "onlyOnB" detail sheets.
     * - A "difference" sheet that, for each field:
     *     • Uses two columns if differences exist (with different background colors),
     *       or one merged column if values are the same.
     *
     * @param clazz           the class of the compared object (e.g. Account.class, Car.class)
     * @param keyAttribute    the name of the key attribute (must be unique)
     * @param collectionA     name of the first collection (side A)
     * @param collectionB     name of the second collection (side B)
     * @param breakCollection name of the collection where ComparisonBreak documents are stored
     * @return an XSSFWorkbook containing the generated report
     */
    public XSSFWorkbook generateExcelReport(Class<?> clazz,
                                            String keyAttribute,
                                            String collectionA,
                                            String collectionB,
                                            String breakCollection) {
        // Retrieve all break records.
        List<ComparisonBreak> allBreaks = mongoTemplate.findAll(ComparisonBreak.class, breakCollection);

        // Group breaks by type for summary.
        Map<String, Long> breaksByType = allBreaks.stream()
                .collect(Collectors.groupingBy(ComparisonBreak::getBreakType, Collectors.counting()));
        long totalBreaks = allBreaks.size();

        // Group breaks by key.
        Map<String, List<ComparisonBreak>> breaksByKey = allBreaks.stream()
                .collect(Collectors.groupingBy(ComparisonBreak::getComparisonKey));

        XSSFWorkbook workbook = new XSSFWorkbook();

        // Create sheets.
        createSummarySheet(workbook, breaksByType, totalBreaks);
        createSimpleDetailSheet(workbook, "onlyOnA", breaksByKey, clazz, keyAttribute, collectionA, null);
        createSimpleDetailSheet(workbook, "onlyOnB", breaksByKey, clazz, keyAttribute, null, collectionB);
        createDifferenceSheet(workbook, breaksByKey, clazz, keyAttribute, collectionA, collectionB);

        return workbook;
    }

    private void createSummarySheet(XSSFWorkbook workbook, Map<String, Long> breaksByType, long totalBreaks) {
        XSSFSheet sheet = workbook.createSheet("Summary");
        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        header.createCell(0).setCellValue("Break Type");
        header.createCell(1).setCellValue("Count");

        String[] types = {"onlyOnA", "onlyOnB", "difference"};
        for (String type : types) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(type);
            row.createCell(1).setCellValue(breaksByType.getOrDefault(type, 0L));
        }
        Row totalRow = sheet.createRow(rowIndex);
        totalRow.createCell(0).setCellValue("Total");
        totalRow.createCell(1).setCellValue(totalBreaks);
    }

    private void createSimpleDetailSheet(XSSFWorkbook workbook,
                                         String breakType,
                                         Map<String, List<ComparisonBreak>> breaksByKey,
                                         Class<?> clazz,
                                         String keyAttribute,
                                         String collectionA,
                                         String collectionB) {
        XSSFSheet sheet = workbook.createSheet(breakType);
        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        int colIndex = 0;
        header.createCell(colIndex++).setCellValue("Key");

        Field[] fields = clazz.getDeclaredFields();
        List<String> fieldNames = new ArrayList<>();
        for (Field f : fields) {
            fieldNames.add(f.getName());
        }
        if ("onlyOnA".equals(breakType)) {
            for (String field : fieldNames) {
                header.createCell(colIndex++).setCellValue(field + " (A)");
            }
        } else if ("onlyOnB".equals(breakType)) {
            for (String field : fieldNames) {
                header.createCell(colIndex++).setCellValue(field + " (B)");
            }
        }

        for (Map.Entry<String, List<ComparisonBreak>> entry : breaksByKey.entrySet()) {
            String key = entry.getKey();
            List<ComparisonBreak> list = entry.getValue();
            boolean hasType = list.stream().anyMatch(b -> b.getBreakType().equals(breakType));
            if (!hasType) continue;
            Object objA = (collectionA != null) ? mongoTemplate.findById(key, clazz, collectionA) : null;
            Object objB = (collectionB != null) ? mongoTemplate.findById(key, clazz, collectionB) : null;
            Row row = sheet.createRow(rowIndex++);
            colIndex = 0;
            row.createCell(colIndex++).setCellValue(key);
            for (String field : fieldNames) {
                if ("onlyOnA".equals(breakType)) {
                    String value = (objA != null) ? getFieldValue(objA, field) : "";
                    row.createCell(colIndex++).setCellValue(value);
                } else if ("onlyOnB".equals(breakType)) {
                    String value = (objB != null) ? getFieldValue(objB, field) : "";
                    row.createCell(colIndex++).setCellValue(value);
                }
            }
        }
    }

    /**
     * Creates the "difference" sheet.
     * For each field: if any record has a difference, two header columns will be used.
     * Then for each record:
     * - If the field is different for that record, two cells are shown (with different cell styles).
     * - If not, the two cells are merged and the common value is displayed.
     */
    private void createDifferenceSheet(XSSFWorkbook workbook,
                                       Map<String, List<ComparisonBreak>> breaksByKey,
                                       Class<?> clazz,
                                       String keyAttribute,
                                       String collectionA,
                                       String collectionB) {
        XSSFSheet sheet = workbook.createSheet("difference");
        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        int colIndex = 0;
        header.createCell(colIndex++).setCellValue("Key");

        // Get declared fields.
        Field[] fields = clazz.getDeclaredFields();
        List<String> fieldNames = new ArrayList<>();
        for (Field f : fields) {
            fieldNames.add(f.getName());
        }
        // Determine for each field whether any record has a difference.
        Map<String, Boolean> fieldDiffMap = new HashMap<>();
        for (String field : fieldNames) {
            fieldDiffMap.put(field, false);
        }
        for (List<ComparisonBreak> list : breaksByKey.values()) {
            for (ComparisonBreak br : list) {
                if ("difference".equals(br.getBreakType())) {
                    fieldDiffMap.put(br.getDifferenceField(), true);
                }
            }
        }
        // Build header row.
        for (String field : fieldNames) {
            if (fieldDiffMap.get(field)) {
                // Field may be different in some records: reserve two columns.
                header.createCell(colIndex++).setCellValue(field + " (A)");
                header.createCell(colIndex++).setCellValue(field + " (B)");
            } else {
                header.createCell(colIndex++).setCellValue(field);
            }
        }

        // Create cell styles for differences.
        CellStyle styleA = workbook.createCellStyle();
        styleA.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        styleA.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle styleB = workbook.createCellStyle();
        styleB.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        styleB.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Process each record (key) that has a "difference" break.
        for (Map.Entry<String, List<ComparisonBreak>> entry : breaksByKey.entrySet()) {
            String key = entry.getKey();
            List<ComparisonBreak> breaksForKey = entry.getValue();
            boolean hasDiffForRecord = breaksForKey.stream().anyMatch(b -> "difference".equals(b.getBreakType()));
            if (!hasDiffForRecord) continue;
            Object objA = (collectionA != null) ? mongoTemplate.findById(key, clazz, collectionA) : null;
            Object objB = (collectionB != null) ? mongoTemplate.findById(key, clazz, collectionB) : null;
            Row row = sheet.createRow(rowIndex++);
            colIndex = 0;
            row.createCell(colIndex++).setCellValue(key);
            // For each field, check if the field is marked as diff-type.
            for (String field : fieldNames) {
                boolean isFieldDiffType = fieldDiffMap.get(field);
                // For this record, does a difference exist for the field?
                boolean isDiffForKey = breaksForKey.stream()
                        .anyMatch(b -> "difference".equals(b.getBreakType()) && b.getDifferenceField().equals(field));
                if (isFieldDiffType) {
                    if (isDiffForKey) {
                        // Field is different: show two cells with different styles.
                        Cell cellA = row.createCell(colIndex++);
                        String valueA = (objA != null) ? getFieldValue(objA, field) : "";
                        cellA.setCellValue(valueA);
                        cellA.setCellStyle(styleA);
                        Cell cellB = row.createCell(colIndex++);
                        String valueB = (objB != null) ? getFieldValue(objB, field) : "";
                        cellB.setCellValue(valueB);
                        cellB.setCellStyle(styleB);
                    } else {
                        // No difference for this record: merge two cells.
                        Cell cell = row.createCell(colIndex);
                        String value = (objA != null) ? getFieldValue(objA, field) : "";
                        cell.setCellValue(value);
                        sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), colIndex, colIndex + 1));
                        colIndex += 2;
                    }
                } else {
                    // Field is never different: only one cell.
                    Cell cell = row.createCell(colIndex++);
                    String value = (objA != null) ? getFieldValue(objA, field) : "";
                    cell.setCellValue(value);
                }
            }
        }
    }

    private String getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            return (value != null) ? value.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
