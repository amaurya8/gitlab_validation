package com.aisa.gitlab.main;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IssueValidator {

    private static final Logger LOGGER = Logger.getLogger(IssueValidator.class.getName());
    private static final String PRIVATE_TOKEN = "your_personal_access_token";
    public static final List<Map<String, String>> issueFailures = new ArrayList<>();

    public void validateIssuesInProjectsUnderGroup(int groupId, Gson gson) {
        fetchAndValidateProjectsInGroup(groupId, gson);
        fetchAndValidateSubgroups(groupId, gson);
    }

    private void fetchAndValidateProjectsInGroup(int groupId, Gson gson) {
        int currentPage = 1;
        int perPage = 50;
        boolean hasMorePages = true;

        while (hasMorePages) {
            LOGGER.log(Level.INFO, "Fetching projects for group {0} - Page: {1}", new Object[]{groupId, currentPage});
            RequestSpecification projectRequest = RestAssured.given()
                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
                    .queryParam("page", currentPage)
                    .queryParam("per_page", perPage);

            Response projectResponse = projectRequest.get("/groups/" + groupId + "/projects");

            if (projectResponse.getStatusCode() != 200) {
                LOGGER.log(Level.SEVERE, "Failed to fetch projects for group {0}. HTTP Error Code: {1}", new Object[]{groupId, projectResponse.getStatusCode()});
                return;
            }

            JsonArray projects = gson.fromJson(projectResponse.getBody().asString(), JsonArray.class);
            LOGGER.log(Level.INFO, "Number of projects fetched for group {0}: {1}", new Object[]{groupId, projects.size()});

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

    private void fetchAndValidateSubgroups(int groupId, Gson gson) {
        int currentPage = 1;
        int perPage = 50;
        boolean hasMorePages = true;

        while (hasMorePages) {
            LOGGER.log(Level.INFO, "Fetching subgroups for group {0} - Page: {1}", new Object[]{groupId, currentPage});
            RequestSpecification subgroupRequest = RestAssured.given()
                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
                    .queryParam("page", currentPage)
                    .queryParam("per_page", perPage);

            Response subgroupResponse = subgroupRequest.get("/groups/" + groupId + "/subgroups");

            if (subgroupResponse.getStatusCode() != 200) {
                LOGGER.log(Level.SEVERE, "Failed to fetch subgroups for group {0}. HTTP Error Code: {1}", new Object[]{groupId, subgroupResponse.getStatusCode()});
                return;
            }

            JsonArray subgroups = gson.fromJson(subgroupResponse.getBody().asString(), JsonArray.class);
            LOGGER.log(Level.INFO, "Number of subgroups fetched for group {0}: {1}", new Object[]{groupId, subgroups.size()});

            for (JsonElement subgroupElement : subgroups) {
                JsonObject subgroup = subgroupElement.getAsJsonObject();
                int subgroupId = subgroup.get("id").getAsInt();
                String subgroupName = subgroup.get("name").getAsString();

                LOGGER.log(Level.INFO, "Recursively validating projects and subgroups under subgroup: {0} (ID: {1})", new Object[]{subgroupName, subgroupId});
                validateIssuesInProjectsUnderGroup(subgroupId, gson);
            }

            String nextPageLink = subgroupResponse.getHeader("X-Next-Page");
            hasMorePages = (nextPageLink != null && !nextPageLink.isEmpty());
            currentPage++;
        }
    }

    private void validateIssuesInProject(int projectId, String projectName, Gson gson) {
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

                validateIssue(issue, issueId, issueLink);
            }

            String nextPageLink = issueResponse.getHeader("X-Next-Page");
            hasMorePages = (nextPageLink != null && !nextPageLink.isEmpty());
            currentPage++;
        }
    }

    private void validateIssue(JsonObject issue, int issueId, String issueLink) {
        boolean hasWeight = issue.has("weight") && !issue.get("weight").isJsonNull();
        boolean isOpen = issue.has("state") && "opened".equalsIgnoreCase(issue.get("state").getAsString());
        boolean isClosed = issue.has("state") && "closed".equalsIgnoreCase(issue.get("state").getAsString());
        boolean closedAfterThreshold = false;
        boolean isLinkedToEpic = issue.has("epic") && !issue.get("epic").isJsonNull();
        boolean isLinkedToCrewDeliveryEpic = false;

        // Extract "Created by" information
        String createdBy = issue.has("author") && issue.getAsJsonObject("author").has("name")
                ? issue.getAsJsonObject("author").get("name").getAsString()
                : "Unknown";

        // Extract "Closed by" information
        String closedBy = (isClosed && issue.has("closed_by") && issue.getAsJsonObject("closed_by").has("name"))
                ? issue.getAsJsonObject("closed_by").get("name").getAsString()
                : "N/A"; // If issue is not closed or closed_by is missing
        // Predefined list of crew delivery epic IDs
        List<Integer> crewDeliveryEpicIds = List.of(2345, 5678, 7890);

        if (isClosed && issue.has("closed_at") && !issue.get("closed_at").isJsonNull()) {
            String closedAt = issue.get("closed_at").getAsString();
            // Check if the closed date is on or after January 1, 2025
            closedAfterThreshold = closedAt.compareTo("2025-01-01T00:00:00Z") >= 0;
        }

        // Check if the issue is linked to an epic and if the epic ID is in the crew delivery list
        if (isLinkedToEpic) {
            JsonObject epic = issue.getAsJsonObject("epic");
            if (epic.has("id") && !epic.get("id").isJsonNull()) {
                int epicId = epic.get("id").getAsInt();
                isLinkedToCrewDeliveryEpic = crewDeliveryEpicIds.contains(epicId);
            }
        }

        // Log failures based on conditions
        if (isOpen && !hasWeight) {
            logIssueFailure(issueId, issueLink, "Open issue missing weight",createdBy,closedBy);
        } else if (closedAfterThreshold && !hasWeight) {
            logIssueFailure(issueId, issueLink, "Closed issue (on/after 2025-01-01) missing weight",createdBy,closedBy);
        }

        if (!isLinkedToEpic) {
            logIssueFailure(issueId, issueLink, "Issue not linked to an epic",createdBy,closedBy);
        } else if (!isLinkedToCrewDeliveryEpic) {
            logIssueFailure(issueId, issueLink, "Issue not linked to a crew delivery epic (epic ID not in the allowed list)",createdBy,closedBy);
        }
    }

    private void logIssueFailure(int issueId, String issueLink, String message, String createdBy, String closedBy) {
        Map<String, String> failure = new HashMap<>();
        failure.put("issue_id", String.valueOf(issueId));
        failure.put("issue_link", issueLink);
        failure.put("failure_message", message);
        failure.put("created_by", createdBy);
        failure.put("closed_by", closedBy);

        issueFailures.add(failure);
        LOGGER.log(Level.WARNING, "Issue validation failure: {0} - {1} | Created by: {2} | Closed by: {3}",
                new Object[]{issueLink, message, createdBy, closedBy});
    }
}