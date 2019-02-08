package master.endpoint;

public class JavaEndpoint {

    public JavaEndpoint(String classDeclaration,
                        String methodDeclaration,
                        String type,
                        String endpoint,
                        String verb) {
        this.classDeclaration = classDeclaration;
        this.methodDeclaration = methodDeclaration;
        this.type = type;
        this.endpoint = endpoint;
        this.verb = verb;
    }

    private String classDeclaration;
    private String methodDeclaration;
    private String type;
    private String endpoint;
    private String verb;

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getMethodDeclaration() {
        return methodDeclaration;
    }

    public void setMethodDeclaration(String methodDeclaration) {
        this.methodDeclaration = methodDeclaration;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getClassDeclaration() {
        return classDeclaration;
    }

    public void setClassDeclaration(String classDeclaration) {
        this.classDeclaration = classDeclaration;
    }

    @Override
    public String toString() {
        return classDeclaration + "." + methodDeclaration + " " + type + " | " + endpoint + " | " + verb;
    }
}