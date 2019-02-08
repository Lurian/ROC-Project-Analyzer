package master.endpoint;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import master.project.ExecUtil;
import master.project.ProjectConfig;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.swing.text.html.Option;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class RestChecker implements RestCheckerApi{

    String projectDir;
    String restDir;
    HashMap<String, String> restClassNamesMap;

    public RestChecker(String pathToProject, ProjectConfig projectConfig, RestConfig restConfig) {
        this.projectDir = pathToProject;
        this.restDir = pathToProject + projectConfig.getRestDir();
        this.restClassNamesMap = restConfig.getRestMap();
    }

    public List<JavaEndpoint> getEndpointsFromDir(){
        List<JavaEndpoint> allJavaEndpoints = new ArrayList<>();

        List<File> files = getAllFiles(restDir);
        for (File file : files) {
            String fileName = file.getName().toLowerCase();
            if(restClassNamesMap.keySet().stream().filter(
                    (restName) -> fileName.contains(restName)).count() > 0) {
                try {
                    List<JavaEndpoint> fileJavaEndpoints = getEndpoints(file);
                    allJavaEndpoints.addAll(fileJavaEndpoints);
                } catch(IOException ex) {
                    // TODO: should it stop execution?
                    System.err.println(ex.getMessage());
                }
            }
        }
        return allJavaEndpoints;
    }

    private Optional<String> getVariableValue(String className, String variableName) throws IOException {
        // Get all files in the project
        List<File> files = getAllFiles(restDir);
        for (File file : files){
            // Extract the compilation unit for each of them
            CompilationUnit compUnit = JavaParser.parse(file);
            // Find all the classes in the file and iterate
            List<ClassOrInterfaceDeclaration> classList = compUnit.findAll(ClassOrInterfaceDeclaration.class);
            for(ClassOrInterfaceDeclaration clazz : classList){
                // If the class is the target class
                if(clazz.getName().toString().equals(className)){
                    // Find all the variable declarations in the class and iterate
                    List<VariableDeclarator> variableList = clazz.findAll(VariableDeclarator.class);
                    for(VariableDeclarator variable : variableList){
                        // If the variable is the target variable, extract the value and return it
                        if(variable.getName().toString().equals(variableName)){
                            String variableValue = variable.getInitializer().get().toString();
                            return Optional.of(variableValue);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> getPathValueFromRedirectMethod(String className) throws IOException {
        // Get all files in the project
        List<File> files = getAllFiles(restDir);
        for (File file : files){
            // Extract the compilation unit for each of them
            CompilationUnit compUnit = JavaParser.parse(file);
            // Find all the classes in the file and iterate
            List<ClassOrInterfaceDeclaration> classList = compUnit.findAll(ClassOrInterfaceDeclaration.class);
            for(ClassOrInterfaceDeclaration clazz : classList){
                // Find all the methods declarations in the class and iterate
                List<MethodDeclaration> methodDeclarationList = clazz.findAll(MethodDeclaration.class);
                for(MethodDeclaration methodDeclaration : methodDeclarationList){
                    if(methodDeclaration.getType().toString().equals(className)){
                        return getEndpointFromPathAnnotation(methodDeclaration.getAnnotations());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String extractPathFromPathAnnotationValue(String pathValue) throws IOException{
        String endpointStart = pathValue;
        // Check if the path value contains an expression
        if(pathValue.contains("+")){
            String firstExpr = pathValue.split("\\+")[0].trim();
            String secondExpr = pathValue.split("\\+")[1].trim();

            if(isVariableConstant(firstExpr)){
                firstExpr = extractVariableValue(firstExpr);
            }

            if(isVariableConstant(firstExpr)){
                secondExpr = extractVariableValue(firstExpr);
            }

            // Removing double quotes and standardizing id notation
            firstExpr = transformIdNotationIntoStandardIdNotation(removeDoubleQuotes(firstExpr));
            secondExpr = transformIdNotationIntoStandardIdNotation(removeDoubleQuotes(secondExpr));
            endpointStart = firstExpr+secondExpr;
        } else {
            // Removing double quotes and standardizing id notation
            endpointStart = transformIdNotationIntoStandardIdNotation(removeDoubleQuotes(endpointStart));
        }

        // Adding slash at the start if not present
        if(!endpointStart.startsWith("/")) {
            endpointStart = "/" + endpointStart;
        }
        return endpointStart;
    }

    private String getEndpointStart(ClassOrInterfaceDeclaration clazz) throws IOException{
        // Get list of annotation on the class
        List<AnnotationExpr> annotationList = clazz.getAnnotations();
        // Search for the value in the @Path annotation
        Optional<String> pathValueOption = getEndpointFromPathAnnotation(annotationList);

        // If the path value is present on the annotation extract it
        if(pathValueOption.isPresent()){
            String pathValue = pathValueOption.get();
            return extractPathFromPathAnnotationValue(pathValue);
        } else {
            pathValueOption = getPathValueFromRedirectMethod(clazz.getNameAsString());
            if(pathValueOption.isPresent()) {
                String pathValue = pathValueOption.get();
                return extractPathFromPathAnnotationValue(pathValue);
            } else {
                //TODO: Makeshift value, dont know that to do to extract path from classes like 'FacadeCorporativoRest'
                return "/";
            }
        }
    }

    private String extractVariableValue(String firstExpr) throws IOException {
        // Splitting into variable class and name
        String variableClazzName = firstExpr.split("\\.")[0];
        String variableName = firstExpr.split("\\.")[1];

        // Searching for value in the project
        Optional<String> variableValueOption = getVariableValue(variableClazzName, variableName);

        if(variableValueOption.isPresent()) return variableValueOption.get();
        throw new RuntimeException("Variable not found!");
    }

    private boolean isVariableConstant(String firstExpr) {
        return firstExpr.contains(".");
    }

    private List<JavaEndpoint> getEndpoints(File file) throws IOException {
        List<JavaEndpoint> javaEndpoints = new ArrayList<>();
        CompilationUnit compUnit = JavaParser.parse(file);

        // Calculating the package name from the directories
        String javaSplitString = ExecUtil.isWindows() ? "\\\\java\\\\" : "/java/";
        String dirsStartingFromJavaDir = file.getAbsolutePath().split(javaSplitString)[1];

        String fileSeparatorSplitString = ExecUtil.isWindows() ? "\\\\" : "/";
        String[] arrayOfDirs = dirsStartingFromJavaDir.split(fileSeparatorSplitString);
        String packageName = arrayOfDirs[0];
        for(int i = 1; i < arrayOfDirs.length - 1; i++) packageName = packageName + "." + arrayOfDirs[i];

        // Getting class of file
        List<ClassOrInterfaceDeclaration> classList = compUnit.findAll(ClassOrInterfaceDeclaration.class);
        if(classList.size() > 1) throw new RuntimeException("More than one class was found in this file.");
        ClassOrInterfaceDeclaration clazz = classList.get(0);

        String type = getEndpointType(clazz);
        if(type.equals("NotAEndpoint")) return javaEndpoints;
        String endpointStart = getEndpointStart(clazz);

        List<MethodDeclaration> methodList = compUnit.findAll(MethodDeclaration.class);
        for (MethodDeclaration methodDeclaration : methodList) {
            boolean hasRestAnnotation = false;
            for (AnnotationExpr a : methodDeclaration.getAnnotations()) {
                hasRestAnnotation = hasRestAnnotation || isRestAnnotation(a.getNameAsString());
            }
            if (hasRestAnnotation) {
                String endpoint = extractEndpointFromAnnotations(endpointStart, methodDeclaration);
                String verb = extractVerbFromAnnotations(methodDeclaration);
                String methodDeclarationString = extractMethodDeclarationString(methodDeclaration);
                String classDeclaration = packageName + "." + clazz.getNameAsString();

                JavaEndpoint javaEndpoint = new JavaEndpoint(classDeclaration, methodDeclarationString, type, endpoint, verb);
                javaEndpoints.add(javaEndpoint);
            }
        }
        return javaEndpoints;
    }

    private String extractMethodDeclarationString(MethodDeclaration methodDeclaration) {
        String methodDeclarationString = methodDeclaration.getDeclarationAsString(false, false, false);
        String returnType = methodDeclarationString.split(" ")[0];
        return methodDeclarationString.substring(returnType.length() + 1);
    }

    private String extractVerbFromAnnotations(MethodDeclaration methodDeclaration) {
        List<AnnotationExpr> annotationList = methodDeclaration.getAnnotations();
        String verb = "NotFound";
        for (AnnotationExpr annotation: annotationList) {
            if (isRestAnnotation(annotation.getNameAsString())) {
                verb = annotation.getNameAsString();
            }
        }
        return verb;
    }

    private String extractEndpointFromAnnotations(String endpointStart, MethodDeclaration methodDeclaration) {
        String endpoint;
        List<AnnotationExpr> annotationList = methodDeclaration.getAnnotations();
        Optional<String> pathValueOption = getEndpointFromPathAnnotation(annotationList);

        String pathValue = "/";
        if(pathValueOption.isPresent()) pathValue =  transformIdNotationIntoStandardIdNotation(removeDoubleQuotes(pathValueOption.get()));

        // Checking if the path contains "/" when concatenating with the endpointStart
        if(pathValue.startsWith("/")) endpoint = endpointStart + pathValue;
        else endpoint = endpointStart + "/" + pathValue;

        if(endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);

        // Removing double slashes
        if(endpoint.startsWith("//")) {
            endpoint = endpoint.replace("//", "/");
        }
        return endpoint;
    }

    private Optional<String> getEndpointFromPathAnnotation(List<AnnotationExpr> annotationList) {
        for (AnnotationExpr annotation: annotationList) {
            if (annotation.getName().asString().equals("Path")) {
                String pathString = annotation.getChildNodes().get(1).toString();
                String pathValueReplaced = removeDoubleQuotes(pathString);
                return Optional.of(pathValueReplaced);
            }
        }
        return Optional.empty();
    }

    private String removeDoubleQuotes(String pathString) {
        return pathString.replace("\"", "");
    }

    private String transformIdNotationIntoStandardIdNotation(String pathString) {
        return pathString.replaceAll("\\{\\w*}", ":id");
    }

    private String getEndpointType(ClassOrInterfaceDeclaration clazz) {
        String clazzName = clazz.getNameAsString().toLowerCase();
        Optional<String> optionalRestMatch = restClassNamesMap.keySet().stream().filter(
                (restName) -> clazzName.contains(restName)).findFirst();
        if(optionalRestMatch.isPresent()){
            return restClassNamesMap.get(optionalRestMatch.get());
        }
        return "NotAEndpoint";
    }

    private boolean isRestAnnotation(String y) {
        List<String> verbs = Arrays.asList(restVerbs);
        return verbs.contains(y);
    }

    String[] restVerbs = {"GET", "PUT", "PATCH", "POST", "HEAD"};

    List<File> getAllFiles(String dir) {
        File folder = new File(dir);
        return getAllFiles(folder);
    }

    List<File> getAllFiles(File folder) {
        if(!folder.isDirectory()) throw new RuntimeException("The path does not correspond to a directory, path:"
                + folder.getAbsolutePath());
        File[] files = folder.listFiles();

        List<File> listOfFiles = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                listOfFiles.add(files[i]);
            } else if (files[i].isDirectory()) {
                listOfFiles.addAll(getAllFiles(files[i]));
            }
        }
        return listOfFiles;
    }
}