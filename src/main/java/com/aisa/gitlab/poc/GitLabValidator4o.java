//package com.aisa.gitlab.main;
//
//import com.google.gson.Gson;
//import com.google.gson.JsonArray;
//import com.google.gson.JsonElement;
//import com.google.gson.JsonObject;
//import io.restassured.RestAssured;
//import io.restassured.response.Response;
//import io.restassured.specification.RequestSpecification;
//
//import java.time.LocalDate;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//import java.util.logging.Logger;
//
//public class GitLabValidator4o {
//
//    private static final Logger LOGGER = Logger.getLogger(GitLabValidator4o.class.getName());
//    private static final String GITLAB_API_BASE_URL = "https://gitlab.com/api/v4";
//    private static final String PRIVATE_TOKEN = "your_personal_access_token";
//    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
//
//    public static void validateGroup(int groupId) {
//        LOGGER.info("Validating group: " + groupId);
//        RestAssured.baseURI = GITLAB_API_BASE_URL;
//        Gson gson = new Gson();
//
//        LOGGER.info("Validating epics under the group...");
//        validateEpics(groupId, gson);
//
//        LOGGER.info("Validating issues in projects under the group...");
//        validateIssues(groupId, gson);
//    }
//
//    private static void validateEpics(int groupId, Gson gson) {
//        int currentPage = 1;
//        int perPage = 50;
//        boolean hasMorePages = true;
//
//        while (hasMorePages) {
//            LOGGER.info("Fetching epics - Page: " + currentPage);
//            RequestSpecification request = RestAssured.given()
//                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
//                    .queryParam("page", currentPage)
//                    .queryParam("per_page", perPage);
//
//            Response response = request.get("/groups/" + groupId + "/epics");
//
//            LOGGER.fine("Response status code: " + response.getStatusCode());
//            LOGGER.fine("Response body: " + response.getBody().asString());
//
//            if (response.getStatusCode() != 200) {
//                LOGGER.severe("Failed to fetch epics. HTTP Error Code: " + response.getStatusCode());
//                return;
//            }
//
//            JsonArray epics = gson.fromJson(response.getBody().asString(), JsonArray.class);
//
//            for (JsonElement epicElement : epics) {
//                JsonObject epic = epicElement.getAsJsonObject();
//                LOGGER.fine("Validating epic: " + epic.toString());
//
//                String createdAt = epic.get("created_at").getAsString();
//
//                if (isCreatedWithinLastYear(createdAt)) {
//                    validateEpic(epic);
//                }
//            }
//
//            String nextPage = response.getHeader("X-Next-Page");
//            hasMorePages = (nextPage != null && !nextPage.isEmpty());
//            currentPage++;
//        }
//    }
//
//    private static void validateEpic(JsonObject epic) {
//        boolean hasStartDate = epic.has("start_date") && !epic.get("start_date").isJsonNull();
//        boolean hasDueDate = epic.has("due_date") && !epic.get("due_date").isJsonNull();
//
//        if (!hasStartDate || !hasDueDate) {
//            LOGGER.warning("Epic ID: " + epic.get("id").getAsInt() + " missing start and/or due date.");
//            ValidationLogger.logEpicFailure(epic.get("id").getAsInt(), epic.get("web_url").getAsString(), "Missing start and/or due date");
//        }
//
//        boolean isCrewDeliveryEpic = epic.has("labels") && containsLabel(epic.getAsJsonArray("labels"), "Crew Delivery Epic");
//        if (isCrewDeliveryEpic) {
//            ValidationLogger.logCrewDeliveryEpic(epic);
//        } else {
//            ValidationLogger.logEpicFailure(epic.get("id").getAsInt(), epic.get("web_url").getAsString(), "Not labeled as Crew Delivery Epic");
//        }
//    }
//
//    private static void validateIssues(int groupId, Gson gson) {
//        int currentPage = 1;
//        int perPage = 50;
//        boolean hasMorePages = true;
//
//        while (hasMorePages) {
//            LOGGER.info("Fetching projects - Page: " + currentPage);
//            RequestSpecification request = RestAssured.given()
//                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
//                    .queryParam("page", currentPage)
//                    .queryParam("per_page", perPage);
//
//            Response response = request.get("/groups/" + groupId + "/projects");
//
//            LOGGER.fine("Response status code: " + response.getStatusCode());
//            LOGGER.fine("Response body: " + response.getBody().asString());
//
//            if (response.getStatusCode() != 200) {
//                LOGGER.severe("Failed to fetch projects. HTTP Error Code: " + response.getStatusCode());
//                return;
//            }
//
//            JsonArray projects = gson.fromJson(response.getBody().asString(), JsonArray.class);
//
//            for (JsonElement projectElement : projects) {
//                JsonObject project = projectElement.getAsJsonObject();
//                LOGGER.info("Validating issues for project ID: " + project.get("id").getAsInt());
//                validateProjectIssues(project.get("id").getAsInt(), gson);
//            }
//
//            String nextPage = response.getHeader("X-Next-Page");
//            hasMorePages = (nextPage != null && !nextPage.isEmpty());
//            currentPage++;
//        }
//    }
//
//    private static void validateProjectIssues(int projectId, Gson gson) {
//        int currentPage = 1;
//        int perPage = 50;
//        boolean hasMorePages = true;
//
//        while (hasMorePages) {
//            LOGGER.info("Fetching issues for project ID: " + projectId + " - Page: " + currentPage);
//            RequestSpecification request = RestAssured.given()
//                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
//                    .queryParam("page", currentPage)
//                    .queryParam("per_page", perPage);
//
//            Response response = request.get("/projects/" + projectId + "/issues");
//
//            LOGGER.fine("Response status code: " + response.getStatusCode());
//            LOGGER.fine("Response body: " + response.getBody().asString());
//
//            if (response.getStatusCode() != 200) {
//                LOGGER.severe("Failed to fetch issues. HTTP Error Code: " + response.getStatusCode());
//                return;
//            }
//
//            JsonArray issues = gson.fromJson(response.getBody().asString(), JsonArray.class);
//
//            for (JsonElement issueElement : issues) {
//                JsonObject issue = issueElement.getAsJsonObject();
//                LOGGER.fine("Validating issue: " + issue.toString());
//
//                String createdAt = issue.get("created_at").getAsString();
//
//                if (isCreatedWithinLastYear(createdAt)) {
//                    validateIssue(issue);
//                }
//            }
//
//            String nextPage = response.getHeader("X-Next-Page");
//            hasMorePages = (nextPage != null && !nextPage.isEmpty());
//            currentPage++;
//        }
//    }
//
//    private static void validateIssue(JsonObject issue) {
//        boolean hasWeight = issue.has("weight") && !issue.get("weight").isJsonNull();
//
//        if (!hasWeight) {
//            LOGGER.warning("Issue ID: " + issue.get("id").getAsInt() + " missing weight.");
//            ValidationLogger.logIssueFailure(issue.get("id").getAsInt(), issue.get("web_url").getAsString(), "Missing weight");
//        }
//    }
//
//    private static boolean isCreatedWithinLastYear(String createdAt) {
//        LocalDate createdDate = LocalDate.parse(createdAt, DATE_FORMATTER.withZone(ZoneId.systemDefault()));
//        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
//        return createdDate.isAfter(oneYearAgo);
//    }
//
//    private static boolean containsLabel(JsonArray labels, String targetLabel) {
//        for (JsonElement label : labels) {
//            if (label.getAsString().equalsIgnoreCase(targetLabel)) {
//                return true;
//            }
//        }
//        return false;
//    }
//}
