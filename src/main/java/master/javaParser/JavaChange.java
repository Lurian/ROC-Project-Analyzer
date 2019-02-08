package master.javaParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaChange {
    private List<JavaLine> lines;
    private String fileName;

    public JavaChange(List<JavaLine> lines, String fileName){
        this.lines = lines;
        this.fileName = fileName;
    }

    public List<JavaLine> getLines() {
        return lines;
    }

    public void setLines(List<JavaLine> lines) {
        this.lines = lines;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return "File: " + fileName + ", lines: " + Arrays.toString(lines.toArray());
    }

    public List<JavaMethod> getImpact(List<JavaMethod> methods) {
        List<JavaMethod> methodsImpacted = new ArrayList<>();
        for(JavaMethod method : methods){
            for(JavaLine line: lines){

                boolean impacted = method.isImpacted(line);
                if(impacted){
                    methodsImpacted.add(method);
                    break;
                }
            }
        }
        return methodsImpacted;
    }
}
