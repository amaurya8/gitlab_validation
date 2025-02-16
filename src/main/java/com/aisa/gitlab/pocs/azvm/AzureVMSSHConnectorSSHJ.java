package com.aisa.gitlab.pocs.azvm;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.IOException;

public class AzureVMSSHConnectorSSHJ {
    public static void main(String[] args) {
        String host = "your-vm-ip-address"; // Replace with Azure VM public IP
        String user = "your-username"; // Replace with your Azure VM username
        String privateKeyPath = "C:/path/to/your/private-key.pem"; // Path to private key

        try (SSHClient ssh = new SSHClient()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier()); // Disables host key verification
            ssh.connect(host);
            ssh.authPublickey(user, privateKeyPath);

            System.out.println("Connected to Azure VM!");

            // Run a remote command
            try (Session session = ssh.startSession()) {
                Session.Command cmd = session.exec("ls -l"); // Example command
                System.out.println("Command output:\n" + cmd.getInputStream().readAllBytes());
                cmd.join();
            }

            System.out.println("Disconnecting...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
