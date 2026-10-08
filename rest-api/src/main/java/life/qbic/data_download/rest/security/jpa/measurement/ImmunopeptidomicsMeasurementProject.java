package life.qbic.data_download.rest.security.jpa.measurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ip_measurements")
public final class ImmunopeptidomicsMeasurementProject implements MeasurementProject {
  @Id
  @Column(name = "measurement_id")
  private String measurementId;

  @Column(name = "measurementCode")
  private String measurementCode;

  @Column(name = "projectId")
  private String projectId;

  public String getProjectId() {
    return projectId;
  }
}
