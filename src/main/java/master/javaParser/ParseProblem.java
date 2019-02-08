package master.javaParser;
import org.bson.Document;

public class ParseProblem {
    private String fileName;
    private String commitId;
    private String tag;
    private String bugId;

    public ParseProblem() {
    }

    public ParseProblem(Document document) {
        this.tag =  document.getString("tag");
        this.bugId =  document.getString("bugId");
        this.fileName =  document.getString("fileName");
        this.commitId =  document.getString("commitId");
    }

    public ParseProblem(String fileName, String commitId, String tag, String bugId) {
        this.fileName = fileName;
        this.commitId = commitId;
        this.tag = tag;
        this.bugId = bugId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getBugId() {
        return bugId;
    }

    public void setBugId(String bugId) {
        this.bugId = bugId;
    }


    public Document getDocument(){
        Document document = new Document();
        document.append("bugId", bugId);
        document.append("tag", tag);
        document.append("fileName",fileName);
        document.append("commitId",commitId);
        return document;
    }

    @Override
    public String toString() {
        return "Tag: " + tag + "| bug: " + bugId + " | File: " + fileName + " | Commit " + commitId;
    }
}
