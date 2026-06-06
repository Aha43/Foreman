package foreman.domain;

import java.util.List;

public record Team(List<RoleAssignment> assignments) {}
