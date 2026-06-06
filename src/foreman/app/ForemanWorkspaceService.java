package foreman.app;

import foreman.domain.ForemanWorkspace;
import foreman.persistence.ForemanWorkspaceRepository;

import java.nio.file.Path;

public class ForemanWorkspaceService {

    private static final Path PROD_PATH = Path.of(System.getProperty("user.home"), ".foreman", "workspace.json");
    private static final Path DEV_PATH  = Path.of(System.getProperty("user.home"), ".foreman", "dev", "workspace.json");

    private final Path workspacePath;
    private final ForemanWorkspaceRepository repository;
    private ForemanWorkspace workspace;

    public ForemanWorkspaceService(boolean devMode) {
        this.workspacePath = devMode ? DEV_PATH : PROD_PATH;
        this.repository = new ForemanWorkspaceRepository();
        this.workspace = repository.load(workspacePath);
    }

    public ForemanWorkspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(ForemanWorkspace workspace) {
        this.workspace = workspace;
    }

    public void save() {
        repository.save(workspace, workspacePath);
    }
}
