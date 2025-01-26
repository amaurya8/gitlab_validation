package com.aisa.gitlab.main;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EpicValidator {

    private static final Logger LOGGER = Logger.getLogger(EpicValidator.class.getName());
    private static final String PRIVATE_TOKEN = "your_personal_access_token";
    public static final List<Map<String, String>> epicFailures = new ArrayList<>();
    public static final List<Map<String, String>> crewDeliveryEpics = new ArrayList<>();
    private static final LocalDate DATE_THRESHOLD = LocalDate.parse("2025-01-01");

    public void validateEpicsUnderGroup(int groupId, Gson gson) {
        int currentPage = 1;
        int perPage = 50;
        boolean hasMorePages = true;

        // Map to store all epics across pages
        Map<Integer, JsonObject> allEpics = new HashMap<>();

        // Step 1: Fetch all epics and accumulate them in allEpics
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

            // Accumulate epics from the current page into the map
            for (JsonElement epicElement : epics) {
                JsonObject epic = epicElement.getAsJsonObject();
                int epicId = epic.get("id").getAsInt();
                allEpics.put(epicId, epic);  // Store epic from the current page
            }

            // Check if there's another page of results
            String nextPageLink = epicResponse.getHeader("X-Next-Page");
            hasMorePages = (nextPageLink != null && !nextPageLink.isEmpty());
            currentPage++;
        }

        // Step 2: Perform validation after accumulating all epics
        for (Map.Entry<Integer, JsonObject> entry : allEpics.entrySet()) {
            JsonObject epic = entry.getValue();
            int epicId = epic.get("id").getAsInt();
            String epicLink = epic.get("web_url").getAsString();
            String createdAt = epic.get("created_at").getAsString();
            String updatedAt = epic.get("updated_at").getAsString();

            // Validate only if the epic is open or closed after 2025-01-01
            if (isCreatedOrUpdatedAfterThreshold(updatedAt)) {
                if (isCreatedWithinLastYear(createdAt)) {
                    validateEpic(epic, epicId, epicLink, createdAt, allEpics);  // Validate using the full map
                }
            }
        }
    }

    private boolean isCreatedOrUpdatedAfterThreshold(String updatedAt) {
        LocalDate updatedDate = LocalDate.parse(updatedAt, DateTimeFormatter.ISO_DATE_TIME);
        return updatedDate.isAfter(DATE_THRESHOLD.minusDays(1)); // After 2025-01-01
    }

    private void validateEpic(JsonObject epic, int epicId, String epicLink, String createdAt, Map<Integer, JsonObject> allEpics) {
        boolean hasStartDate = epic.has("start_date") && !epic.get("start_date").isJsonNull();
        boolean hasDueDate = epic.has("due_date") && !epic.get("due_date").isJsonNull();

        if (!hasStartDate || !hasDueDate) {
            logEpicFailure(epicId, epicLink, "Missing start and/or due date");
        }

        // Check if the epic has the label "Crew Delivery Epic"
        boolean isCrewDeliveryEpic = epic.has("labels") && containsLabel(epic.getAsJsonArray("labels"), "Crew Delivery Epic");
        if (isCrewDeliveryEpic || isPartOfCrewDeliveryEpic(epic, allEpics)) {
            logCrewDeliveryEpic(epicId, epicLink, createdAt);
        } else {
            logEpicFailure(epicId, epicLink, "Neither labeled as Crew Delivery Epic nor part of a Crew Delivery Epic hierarchy");
        }
    }

    private boolean isPartOfCrewDeliveryEpic(JsonObject epic, Map<Integer, JsonObject> allEpics) {
        while (epic != null) {
            // Check if the current epic has the "Crew Delivery Epic" label
            if (epic.has("labels") && containsLabel(epic.getAsJsonArray("labels"), "Crew Delivery Epic")) {
                return true;
            }

            // Move to the parent epic if available
            if (epic.has("parent_id") && !epic.get("parent_id").isJsonNull()) {
                int parentId = epic.get("parent_id").getAsInt();
                epic = allEpics.get(parentId); // Fetch the parent epic from the map
            } else {
                epic = null; // No parent, end the loop
            }
        }

        return false; // No "Crew Delivery Epic" label found in the hierarchy
    }

    private boolean containsLabel(JsonArray labels, String targetLabel) {
        for (JsonElement label : labels) {
            if (label.getAsString().equalsIgnoreCase(targetLabel)) {
                return true;
            }
        }
        return false;
    }

    private void logCrewDeliveryEpic(int epicId, String epicLink, String createdAt) {
        Map<String, String> crewEpic = new HashMap<>();
        crewEpic.put("epic_id", String.valueOf(epicId));
        crewEpic.put("epic_link", epicLink);
        crewEpic.put("created_at", createdAt);
        crewDeliveryEpics.add(crewEpic);
        LOGGER.log(Level.INFO, "Crew Delivery Epic found: {0}", epicLink);
    }

    private static boolean isCreatedWithinLastYear(String createdAt) {
        LocalDate createdDate = LocalDate.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME);
        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        return createdDate.isAfter(oneYearAgo);
    }

    private void logEpicFailure(int epicId, String epicLink, String message) {
        Map<String, String> failure = new HashMap<>();
        failure.put("epic_id", String.valueOf(epicId));
        failure.put("epic_link", epicLink);
        failure.put("failure_message", message);
        epicFailures.add(failure);
        LOGGER.log(Level.WARNING, "Epic validation failure: {0} - {1}", new Object[]{epicLink, message});
    }
}