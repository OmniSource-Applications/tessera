package live.omnisource.tessera.security.rbac.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import live.omnisource.tessera.security.rbac.TesseraRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RbacSubjectBinding {

    /**
     * Recommended formats:
     *  - "oidc:sub:<uuid-or-string>"
     *  - "oidc:email:<email>"
     *  - "oidc:username:<preferred_username>"
     */
    private String subject;

    private List<TesseraRole> globalRoles = new ArrayList<>();

    /**
     * workspace -> roles in that workspace
     */
    private Map<String, List<TesseraRole>> workspaceRoles = new HashMap<>();

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public List<TesseraRole> getGlobalRoles() {
        return globalRoles;
    }

    public void setGlobalRoles(List<TesseraRole> globalRoles) {
        this.globalRoles = globalRoles;
    }

    public Map<String, List<TesseraRole>> getWorkspaceRoles() {
        return workspaceRoles;
    }

    public void setWorkspaceRoles(Map<String, List<TesseraRole>> workspaceRoles) {
        this.workspaceRoles = workspaceRoles;
    }
}
