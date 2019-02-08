package master.javaParser;

public class JavaMethod {

    private int start;
    private int end;
    private String name;

    public JavaMethod(int start, int end, String name) {
        this.start = start;
        this.end = end;
        this.name = name;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name + " - " + start + ":" + end;
    }

    public boolean isImpacted(JavaLine line) {
        int L1 = line.getStart();
        int L2 = line.getEnd();
        int M1 = start;
        int M2 = end;

        return L1 <= M2 && M1 <= L2;
    }
}
