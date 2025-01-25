package com.aisa.gitlab.main;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import static com.aisa.gitlab.main.EpicValidator.crewDeliveryEpics;
import static com.aisa.gitlab.main.EpicValidator.epicFailures;
import static com.aisa.gitlab.main.IssueValidator.issueFailures;

public class ReportGenerator {
    private static final Logger LOGGER = Logger.getLogger(IssueValidator.class.getName());

    public static void generateExcelReport() throws IOException {

        LOGGER.info("Generating Excel report...");
        Workbook workbook = new XSSFWorkbook();
        Sheet epicSheet = workbook.createSheet("Epic Failures");
        Sheet issueSheet = workbook.createSheet("Issue Failures");
        Sheet crewEpicSheet = workbook.createSheet("Crew Delivery Epics");

        // Create headers for the epic sheet
        Row epicHeader = epicSheet.createRow(0);
        epicHeader.createCell(0).setCellValue("Epic ID");
        epicHeader.createCell(1).setCellValue("Epic Link");
        epicHeader.createCell(2).setCellValue("Failure Message");

        // Populate epic failures
        int epicRowNum = 1;
        for (Map<String, String> failure : epicFailures) {
            Row row = epicSheet.createRow(epicRowNum++);
            row.createCell(0).setCellValue(failure.get("epic_id"));
            row.createCell(1).setCellValue(failure.get("epic_link"));
            row.createCell(2).setCellValue(failure.get("failure_message"));
        }

        // Create headers for the issue sheet
        Row issueHeader = issueSheet.createRow(0);
        issueHeader.createCell(0).setCellValue("Issue ID");
        issueHeader.createCell(1).setCellValue("Issue Link");
        issueHeader.createCell(2).setCellValue("Failure Message");

        // Populate issue failures
        int issueRowNum = 1;
        for (Map<String, String> failure : issueFailures) {
            Row row = issueSheet.createRow(issueRowNum++);
            row.createCell(0).setCellValue(failure.get("issue_id"));
            row.createCell(1).setCellValue(failure.get("issue_link"));
            row.createCell(2).setCellValue(failure.get("failure_message"));
        }

        // Create headers for the Crew Delivery Epics sheet
        Row crewEpicHeader = crewEpicSheet.createRow(0);
        crewEpicHeader.createCell(0).setCellValue("Epic ID");
        crewEpicHeader.createCell(1).setCellValue("Epic Link");
        crewEpicHeader.createCell(2).setCellValue("Created At");

        // Populate Crew Delivery epics
        int crewEpicRowNum = 1;
        for (Map<String, String> crewEpic : crewDeliveryEpics) {
            Row row = crewEpicSheet.createRow(crewEpicRowNum++);
            row.createCell(0).setCellValue(crewEpic.get("epic_id"));
            row.createCell(1).setCellValue(crewEpic.get("epic_link"));
            row.createCell(2).setCellValue(crewEpic.get("created_at"));
        }

        try (FileOutputStream fos = new FileOutputStream("GitLab_Validation_Report.xlsx")) {
            workbook.write(fos);
        }

        workbook.close();
        LOGGER.info("Excel report generated successfully.");
    }
}
