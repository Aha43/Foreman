package foreman.domain;

public record Project(String id, String name, String path, String description, Team team, String workflowPath) {

    public String effectiveWorkflowPath() {
        return workflowPath != null && !workflowPath.isBlank() ? workflowPath : path;
    }
}
