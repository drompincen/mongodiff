package com.example.comparison.service;

import com.example.comparison.model.Account;
import com.example.comparison.model.ComparisonBreak;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExcelReportServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ExcelReportService excelReportService;

    @Test
    public void testGenerateExcelReport() throws IOException {
        // Create dummy ComparisonBreak objects.
        List<ComparisonBreak> dummyBreaks = new ArrayList<>();
        // For key "acct001": simulate a difference on the "balance" field.
        dummyBreaks.add(new ComparisonBreak("acct001", "balance", "1000.0", "1100.0", "difference"));
        // For key "acct002": simulate a record only present on side A.
        dummyBreaks.add(new ComparisonBreak("acct002", "RecordMissing", "exists", "missing", "onlyOnA"));
        // For key "acct003": simulate a record only present on side B.
        dummyBreaks.add(new ComparisonBreak("acct003", "RecordMissing", "missing", "exists", "onlyOnB"));

        // Stub the findAll call to return our dummy break list.
        when(mongoTemplate.findAll(ComparisonBreak.class, "breakCollection")).thenReturn(dummyBreaks);

        // Create dummy Account objects.
        Account account1A = createDummyAccount("acct001", "Test A1", 1000.0);
        Account account1B = createDummyAccount("acct001", "Test A1", 1100.0);
        Account account2A = createDummyAccount("acct002", "Test A2", 2000.0);
        Account account3B = createDummyAccount("acct003", "Test A3", 3000.0);

        // Stub findById for key "acct001" (both collections).
        when(mongoTemplate.findById("acct001", Account.class, "collectionA")).thenReturn(account1A);
        when(mongoTemplate.findById("acct001", Account.class, "collectionB")).thenReturn(account1B);
        // For onlyOnA: key "acct002" exists only in collectionA.
        when(mongoTemplate.findById("acct002", Account.class, "collectionA")).thenReturn(account2A);
        when(mongoTemplate.findById("acct002", Account.class, "collectionB")).thenReturn(null);
        // For onlyOnB: key "acct003" exists only in collectionB.
        when(mongoTemplate.findById("acct003", Account.class, "collectionA")).thenReturn(null);
        when(mongoTemplate.findById("acct003", Account.class, "collectionB")).thenReturn(account3B);

        // Generate the Excel workbook.
        XSSFWorkbook workbook = excelReportService.generateExcelReport(
                Account.class,
                "accountId",
                "collectionA",
                "collectionB",
                "breakCollection"
        );

        // Validate that the workbook is generated and contains the expected sheets.
        assertNotNull(workbook);
        assertNotNull(workbook.getSheet("Summary"));
        assertNotNull(workbook.getSheet("onlyOnA"));
        assertNotNull(workbook.getSheet("onlyOnB"));
        assertNotNull(workbook.getSheet("difference"));

        // Check the summary sheet.
        Sheet summarySheet = workbook.getSheet("Summary");
        // Expecting header, one row per break type, and a total row (5 rows in total).
        assertEquals(5, summarySheet.getPhysicalNumberOfRows(), "Summary sheet should have 5 rows");

        // Verify header and total values.
        Row headerRow = summarySheet.getRow(0);
        assertEquals("Break Type", headerRow.getCell(0).getStringCellValue());
        Row totalRow = summarySheet.getRow(4);
        assertEquals("Total", totalRow.getCell(0).getStringCellValue());
        assertEquals(3, (int) totalRow.getCell(1).getNumericCellValue(), "Total breaks should equal 3");
        // Save the workbook to a file in the working directory.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
        String timestamp = sdf.format(new Date());
        String fileName = "ExcelReport_" + timestamp + ".xlsx";
        File outputFile = new File(fileName);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            workbook.write(fos);
        }
        workbook.close();

        // Print the full path of the generated file.
        System.out.println("Excel report saved to: " + outputFile.getAbsolutePath());

        // Basic check: file should exist and be non-empty.
        assertTrue(outputFile.exists(), "Excel file should exist");
        assertTrue(outputFile.length() > 0, "Excel file should not be empty");
    }

    private Account createDummyAccount(String accountId, String name, double balance) {
        Account acc = new Account();
        acc.setAccountId(accountId);
        acc.setAccountName(name);
        acc.setBalance(balance);
        // Set dummy values for other fields.
        acc.setAccountType("Cash");
        acc.setBroker("DummyBroker");
        acc.setCreationDate(new Date());
        acc.setCurrency("USD");
        acc.setRiskLevel("Medium");
        acc.setLastTradeDate(new Date());
        acc.setTotalTrades(10);
        acc.setAvailableMargin(100.0);
        acc.setEmail(accountId + "@example.com");
        acc.setPhoneNumber("1234567890");
        acc.setAddress("123 Test St");
        acc.setCountry("TestCountry");
        acc.setState("TestState");
        acc.setCity("TestCity");
        acc.setZipCode("00000");
        acc.setInvestmentStyle("TestStyle");
        acc.setAccountStatus("Active");
        return acc;
    }
}
