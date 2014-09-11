package amplify;


public enum GABuildType {
    DESIGN ("DesignTest"),
    QA ("QATest");

    private final String stringRepresentation;

    GABuildType(String stringRepresentation) {
        this.stringRepresentation = stringRepresentation;
    }

    @Override
    public String toString() {
        return stringRepresentation;
    }
}
