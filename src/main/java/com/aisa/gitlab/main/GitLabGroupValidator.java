package com.aisa.gitlab.main;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitLabGroupValidator {

    private static final Logger LOGGER = Logger.getLogger(GitLabGroupValidator.class.getName());
    private static final String GITLAB_API_BASE_URL = "https://gitlab.com/api/v4";
    private static final String PRIVATE_TOKEN = "your_personal_access_token";

    public void validateGroups(Set<Integer> groupIds) {
        for (int groupId : groupIds) {
            LOGGER.info("Validating group with ID: " + groupId);
            validateGroup(groupId);
        }
    }

    private void validateGroup(int groupId) {
        RestAssured.baseURI = GITLAB_API_BASE_URL;
        Gson gson = new Gson();

        LOGGER.info("Validating epics under the group...");
        EpicValidator epicValidator = new EpicValidator();
        epicValidator.validateEpicsUnderGroup(groupId, gson);

        LOGGER.info("Validating issues in projects under the group...");
        IssueValidator issueValidator = new IssueValidator();
        issueValidator.validateIssuesInProjectsUnderGroup(groupId, gson);
    }

    private String getGroupName(int groupId) {
        RequestSpecification groupRequest = RestAssured.given()
                .header("PRIVATE-TOKEN", PRIVATE_TOKEN);

        Response groupResponse = groupRequest.get("/groups/" + groupId);

        if (groupResponse.getStatusCode() != 200) {
            LOGGER.log(Level.SEVERE, "Failed to fetch group details. HTTP Error Code: {0}", groupResponse.getStatusCode());
            return "Unknown Group"; // Fallback value in case of failure
        }

        JsonObject groupDetails = new Gson().fromJson(groupResponse.getBody().asString(), JsonObject.class);
        return groupDetails.has("name") ? groupDetails.get("name").getAsString() : "Unknown Group";
    }
}