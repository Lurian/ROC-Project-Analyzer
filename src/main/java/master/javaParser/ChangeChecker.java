package master.javaParser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.util.*;

public class ChangeChecker {

    /**
     * Calculates the ImpactMap in FilaName <-> Methods
     */
    static public JavaImpact calculateChange(JavaChange change, String file) throws IOException {
        List<JavaMethod> methods = new ArrayList<>();
        try {
            // This is where the JavaParser lib is used to parse the java file.
            CompilationUnit compUnit = JavaParser.parse(file);
            // The method visitor calculates the methods present in the file.
            MethodVisitor methodVisitor = new MethodVisitor(new ArrayList<>());
            compUnit.accept(methodVisitor, null);
            // The methods are extracted through the 'methods' field in the Visitor
            methods = methodVisitor.methods;
        } catch (Exception ex){
            // If any problem happens in the parsing step it is ignored nad logged
            System.err.println(ex.getMessage());
            // A ParseProblem is created with filename, commit, tag and bugId information
//            ParseProblem parseProblem =
//                    new ParseProblem(fileName, commitId, tag, bugId);
//            // And stored in the DB
//            saveParseProblem(parseProblem);
        }

        List<JavaMethod> methodsImpacted = change.getImpact(methods);

        Set<String> methodNamesImpacted = new HashSet<>();
        for(JavaMethod method : methodsImpacted){
            methodNamesImpacted.add(method.getName());
        }
        return new JavaImpact(methodNamesImpacted, change.getFileName());
    }

    /**
     * Simple visitor implementation for visiting MethodDeclaration nodes.
     * Used as a helper in calculating the existing methods in a file.
     */
    static private class MethodVisitor extends VoidVisitorAdapter<Void> {

        // Public field storing the methods found.
        public List<JavaMethod> methods;

        public MethodVisitor(List<JavaMethod> methods) {
            this.methods = methods;
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            // Not safe positioning extraction.
            JavaMethod method = new JavaMethod(n.getBegin().get().line, n.getEnd().get().line, n.getNameAsString());
            methods.add(method);
            super.visit(n, arg);
        }
    }


    /**
     * Simple visitor implementation for visiting MethodDeclaration nodes.
     * Used as a helper in calculating the existing methods in a file.
     */
    static public class AnonVisitor extends VoidVisitorAdapter<Void> {

        // Public field storing the methods found.
        public List<JavaMethod> methods;

        public AnonVisitor() {}

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            // Not safe positioning extraction.
            String methodName = n.getNameAsString();
            List lista = n.getAnnotations();
            Iterator i = lista.iterator();
            while(i.hasNext()){
                Object x = i.next();
                AnnotationExpr y = (AnnotationExpr) x;
//                System.out.println(y);
                if(isRestAnnotation(y)){
                    String verb = getVerb(y);
                    AnnotationExpr z = getPathAnnotation(i);
                    String path = getPath(z);
                    // add to list
                    System.out.println(methodName + " # " + verb + " | " + path);
                }
            }
            super.visit(n, arg);
        }

        private String getPath(AnnotationExpr z) {
            return z.getChildNodes().get(1).toString();
        }

        private AnnotationExpr getPathAnnotation(Iterator i) {
            while(i.hasNext()){
                Object x = i.next();
                AnnotationExpr y = (AnnotationExpr) x;
                if(y.getName().asString().equals("Path")) return y;
            }
            throw new RuntimeException("PATH not found");
        }

        private String getVerb(AnnotationExpr y) {
            return y.getName().asString();
        }

        private boolean isRestAnnotation(AnnotationExpr y) {
           List<String> verbs =  Arrays.asList(restVerbs);
           return verbs.contains(y.getNameAsString());
        }

        String[] restVerbs = {"GET", "PUT", "PATCH", "POST", "HEAD"};
    }
}
