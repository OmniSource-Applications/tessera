package live.omnisource.tessera.security.rbac.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RbacPolicyDocument {

  private int schemaVersion = 1;
  private List<RbacSubjectBinding> subjects = new ArrayList<>();

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public List<RbacSubjectBinding> getSubjects() {
    return subjects;
  }

  public void setSubjects(List<RbacSubjectBinding> subjects) {
    this.subjects = subjects;
  }
}
