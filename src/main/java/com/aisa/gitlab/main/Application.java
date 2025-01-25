package com.aisa.gitlab.main;


import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Application {

    public static void main(String[] args) throws IOException {
        // Use a Set with a one-liner initialization
        Set<Integer> groupIds = new HashSet<>(Arrays.asList(12345, 67890, 11223, 12345)); // Duplicates will be ignored

        GitLabGroupValidator groupValidator = new GitLabGroupValidator();
        groupValidator.validateGroups(groupIds);
        ReportGenerator.generateExcelReport();
    }
}