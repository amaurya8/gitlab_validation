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

        if (!hasWeight) {
            logIssueFailure(issueId, issueLink, "Missing weight");
        }
    }

    private void logIssueFailure(int issueId, String issueLink, String message) {
        Map<String, String> failure = new HashMap<>();
        failure.put("issue_id", String.valueOf(issueId));
        failure.put("issue_link", issueLink);
        failure.put("failure_message", message);
        issueFailures.add(failure);
        LOGGER.log(Level.WARNING, "Issue validation failure: {0} - {1}", new Object[]{issueLink, message});
    }
}