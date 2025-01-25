package com.aisa.gitlab.poc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitLabGroupValidation {

    private static final Logger LOGGER = Logger.getLogger(GitLabGroupValidation.class.getName());
    private static final String GITLAB_API_BASE_URL = "https://gitlab.com/api/v4";
    private static final String PRIVATE_TOKEN = "your_personal_access_token";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private static final List<Map<String, String>> epicFailures = new ArrayList<>();
    private static final List<Map<String, String>> issueFailures = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        LOGGER.info("Starting GitLab Group Validation...");
        int groupId = 12345; // Replace with your GitLab group ID
        validateGroupEpicsAndIssues(groupId);

        // Generate the Excel report
        generateExcelReport();
        LOGGER.info("GitLab Group Validation completed.");
    }

    public static void validateGroupEpicsAndIssues(int groupId) {
        RestAssured.baseURI = GITLAB_API_BASE_URL;
        Gson gson = new Gson();

        LOGGER.info("Validating epics under the group...");
        validateEpicsUnderGroup(groupId, gson);

        LOGGER.info("Validating issues in projects under the group...");
        validateIssuesInProjectsUnderGroup(groupId, gson);
    }

    private static void validateEpicsUnderGroup(int groupId, Gson gson) {
        int currentPage = 1;
        int perPage = 50;
        boolean hasMorePages = true;

        while (hasMorePages) {
            LOGGER.log(Level.INFO, "Fetching epics - Page: {0}", currentPage);
            RequestSpecification epicRequest = RestAssured.given()
                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
                    .queryParam("page", currentPage)
                    .queryParam("per_page", perPage);

            Response epicResponse = epicRequest.get("/groups/" + groupId + "/epics");

            if (epicResponse.getStatusCode() != 200) {
                LOGGER.log(Level.SEVERE, "Failed to fetch epics. HTTP Error Code: {0}", epicResponse.getStatusCode());
                return;
            }

            JsonArray epics = gson.fromJson(epicResponse.getBody().asString(), JsonArray.class);
            LOGGER.log(Level.INFO, "Number of epics fetched: {0}", epics.size());

            if (epics.size() == 0) {
                hasMorePages = false;
                break;
            }

            for (JsonElement epicElement : epics) {
                JsonObject epic = epicElement.getAsJsonObject();
                int epicId = epic.get("id").getAsInt();
                String epicLink = epic.get("web_url").getAsString();
                String createdAt = epic.get("created_at").getAsString();

                if (isCreatedWithinLastYear(createdAt)) {
                    validateEpic(epic, epicId, epicLink);
                }
            }

            String nextPageLink = epicResponse.getHeader("X-Next-Page");
            hasMorePages = (nextPageLink != null && !nextPageLink.isEmpty());
            currentPage++;
        }
    }

    private static void validateEpic(JsonObject epic, int epicId, String epicLink) {
        boolean hasStartDate = epic.has("start_date") && !epic.get("start_date").isJsonNull();
        boolean hasDueDate = epic.has("due_date") && !epic.get("due_date").isJsonNull();

        if (!hasStartDate || !hasDueDate) {
            logEpicFailure(epicId, epicLink, "Missing start and/or due date");
        }

        // Crew Delivery Epic Check
        boolean isCrewDeliveryEpic = epic.has("title") && epic.get("title").getAsString().toLowerCase().contains("crew delivery");
        if (!isCrewDeliveryEpic) {
            logEpicFailure(epicId, epicLink, "Not a Crew Delivery epic");
        }
    }

    private static void validateIssuesInProjectsUnderGroup(int groupId, Gson gson) {
        int currentPage = 1;
        int perPage = 50;
        boolean hasMorePages = true;

        while (hasMorePages) {
            LOGGER.log(Level.INFO, "Fetching projects - Page: {0}", currentPage);
            RequestSpecification projectRequest = RestAssured.given()
                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
                    .queryParam("page", currentPage)
                    .queryParam("per_page", perPage);

            Response projectResponse = projectRequest.get("/groups/" + groupId + "/projects");

            if (projectResponse.getStatusCode() != 200) {
                LOGGER.log(Level.SEVERE, "Failed to fetch projects. HTTP Error Code: {0}", projectResponse.getStatusCode());
                return;
            }

            JsonArray projects = gson.fromJson(projectResponse.getBody().asString(), JsonArray.class);
            LOGGER.log(Level.INFO, "Number of projects fetched: {0}", projects.size());

            if (projects.size() == 0) {
                hasMorePages = false;
                break;
            }

            for (JsonElement projectElement : projects) {
                JsonObject project = projectElement.getAsJsonObject();
                int projectId = project.get("id").getAsInt();
                String projectName = project.get("name").getAsString();

                LOGGER.log(Level.INFO, "Validating issues for project: {0} (ID: {1})", new Object[]{projectName, projectId});
                validateIssuesInProject(projectId, projectName, gson);
            }

            String nextPageLink = projectResponse.getHeader("X-Next-Page");
            hasMorePages = (nextPageLink != null && !nextPageLink.isEmpty());
            currentPage++;
        }
    }

    private static void validateIssuesInProject(int projectId, String projectName, Gson gson) {
        int currentPage = 1;
        int perPage = 50;
        boolean hasMorePages = true;

        while (hasMorePages) {
            RequestSpecification issueRequest = RestAssured.given()
                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
                    .queryParam("page", currentPage)
                    .queryParam("per_page", perPage);

            Response issueResponse = issueRequest.get("/projects/" + projectId + "/issues");

            if (issueResponse.getStatusCode() != 200) {
                LOGGER.log(Level.SEVERE, "Failed to fetch issues for project '{0}'. HTTP Error Code: {1}", new Object[]{projectName, issueResponse.getStatusCode()});
                return;
            }

            JsonArray issues = gson.fromJson(issueResponse.getBody().asString(), JsonArray.class);
            LOGGER.log(Level.INFO, "Number of issues fetched for project '{0}': {1}", new Object[]{projectName, issues.size()});

            for (JsonElement issueElement : issues) {
                JsonObject issue = issueElement.getAsJsonObject();
                int issueId = issue.get("id").getAsInt();
                String issueLink = issue.get("web_url").getAsString();
                String createdAt = issue.get("created_at").getAsString();

                if (isCreatedWithinLastYear(createdAt)) {
                    validateIssue(issue, issueId, issueLink);
                }
            }

            String nextPageLink = issueResponse.getHeader("X-Next-Page");
            hasMorePages = (nextPageLink != null && !nextPageLink.isEmpty());
            currentPage++;
        }
    }

    private static void validateIssue(JsonObject issue, int issueId, String issueLink) {
        boolean hasWeight = issue.has("weight") && !issue.get("weight").isJsonNull();

        if (!hasWeight) {
            logIssueFailure(issueId, issueLink, "Missing weight");
        }
    }

    private static void logEpicFailure(int epicId, String epicLink, String message) {
        Map<String, String> failure = new HashMap<>();
        failure.put("epic_id", String.valueOf(epicId));
        failure.put("epic_link", epicLink);
        failure.put("failure_message", message);
        epicFailures.add(failure);
        LOGGER.log(Level.WARNING, "Epic validation failure: {0} - {1}", new Object[]{epicLink, message});
    }

    private static void logIssueFailure(int issueId, String issueLink, String message) {
        Map<String, String> failure = new HashMap<>();
        failure.put("issue_id", String.valueOf(issueId));
        failure.put("issue_link", issueLink);
        failure.put("failure_message", message);
        issueFailures.add(failure);
        LOGGER.log(Level.WARNING, "Issue validation failure: {0} - {1}", new Object[]{issueLink, message});
    }

    private static boolean isCreatedWithinLastYear(String createdAt) {
        LocalDate createdDate = LocalDate.parse(createdAt, DATE_FORMATTER);
        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        return createdDate.isAfter(oneYearAgo);
    }

    private static void generateExcelReport() throws IOException {
        LOGGER.info("Generating Excel report...");
        Workbook workbook = new XSSFWorkbook();
        Sheet epicSheet = workbook.createSheet("Epic Failures");
        Sheet issueSheet = workbook.createSheet("Issue Failures");

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

        try (FileOutputStream fos = new FileOutputStream("GitLab_Validation_Report.xlsx")) {
            workbook.write(fos);
        }

        workbook.close();
        LOGGER.info("Excel report generated successfully.");
    }
}
