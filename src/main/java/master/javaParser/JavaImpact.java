package master.javaParser;

import java.util.*;

public class JavaImpact {
    private Set<String> methods;
    private String fileName;

    public JavaImpact(Set<String> methods, String fileName) {
        this.methods = methods;
        this.fileName = fileName;
    }

    public Set<String> getMethods() {
        return methods;
    }

    public void setMethods(Set<String> methods) {
        this.methods = methods;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return "File: " + fileName + ", methods: " + Arrays.toString(methods.toArray());
    }
}
