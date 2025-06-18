package com.example.robotcarsecurity.Services;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


public class ApiKeyService {
    public String getApiKeyHash() {
        String rawKey = "api_key"; // Store this securely
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
