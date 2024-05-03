// Health check script for Keycloak Docker container, to be used in Docker Compose.
public class HealthCheck {
    public static void main(String[] args) throws java.lang.Throwable {
        String url = "http://localhost:8080/health/live";
        int responseCode = ((java.net.HttpURLConnection) new java.net.URL(url).openConnection()).getResponseCode();
        System.exit(java.net.HttpURLConnection.HTTP_OK == responseCode ? 0 : 1);
    }
}
