package amplify;


public enum GASeverity {
    CRITICAL ("critical"),
    ERROR ("error"),
    WARNING ("warning"),
    INFO ("info"),
    DEBUG ("debug");

    private final String severity;

    GASeverity(String severity) {
        this.severity = severity;
    }

    @Override
    public String toString() {
        return severity;
    }
}
