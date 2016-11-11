package com.github.redhatqe.polarize.metadata;

/**
 * This class is used to hold data for discrepancies between annotations and XML
 */
public class WarningInfo {
    public String message;
    public String method;
    public String project;
    public WarningType wt;

    public WarningInfo(String msg, String m, String p, WarningType wt) {
        this.message = msg;
        this.method = m;
        this.project = p;
        this.wt = wt;
    }

    public enum WarningType {
        EmptyAnnotationButIDExists,    // The annotation testCaseID is "", but it exists in the XML
        UpdateButIDExists;             // update was set to true, but the ID exists

        public String message() {
            String msg = "";
            switch(this) {
                case EmptyAnnotationButIDExists:
                    msg = "The annotation testCaseID is empty, but it exists in the XML";
                    break;
                case UpdateButIDExists:
                    msg = "The update field was set to true in annotation, but the ID exists. Did you really" +
                            " change any fields in your annotation?";
                    break;
            }
            return msg;
        }
    }
}
