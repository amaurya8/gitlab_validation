package com.aisa.gitlab.pocs.azvm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AzureVMSSHConnectorNative {
    public static void main(String[] args) {
        String host = "your-vm-ip-address"; // Replace with your Azure VM public IP
        String user = "your-username"; // Replace with your Azure VM username
        String privateKeyPath = "C:/path/to/your/private-key.pem"; // Replace with the correct private key path

        List<String> command = new ArrayList<>();
        command.add("ssh");
        command.add("-i");
        command.add(privateKeyPath);
        command.add("-o");
        command.add("StrictHostKeyChecking=no"); // Skip host verification
        command.add(user + "@" + host);
        command.add("ls -l"); // Replace with the command you want to execute

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // Merge stderr with stdout
            Process process = processBuilder.start();

            // Read command output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("SSH command exited with code: " + exitCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
