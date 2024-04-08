package life.qbic.data_download.rest.security.acl;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.AclPermissionEvaluator;
import org.springframework.security.acls.model.AclService;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.core.Authentication;

/**
 * <b>QBiC's implementation of the Spring PermissionEvaluator interface</b>
 * <p>
 * This class shall be used to check if the current user has the permission to have access to the
 * targetDomainObject of interest in the context of user authorization
 */
public class QbicPermissionEvaluator extends AclPermissionEvaluator {

  private static final String MEASUREMENT_OBJECT_TYPE = "qbic.measurement";
  private static final String PROJECT_OBJECT_TYPE = "life.qbic.projectmanagement.domain.model.project.Project";
  private final MeasurementMappingService measurementMappingService;

  public QbicPermissionEvaluator(@Autowired AclService aclService,
      MeasurementMappingService measurementMappingService) {
    super(aclService);
    this.measurementMappingService = requireNonNull(measurementMappingService,
        "measurementMappingService must not be null");
  }

  private static boolean isEmptyOptional(Object object) {
    if (object instanceof Optional<?>) {
      return ((Optional<?>) object).isEmpty();
    }
    return false;
  }

  @Override
  public boolean hasPermission(Authentication authentication, Serializable targetId,
      String targetType, Object permission) {
    if (MEASUREMENT_OBJECT_TYPE.equals(targetType) && targetId instanceof String objectId) {
      // turn it into a project
      var projectId = measurementMappingService.projectIdForMeasurement(objectId)
          .orElseThrow(() -> new NotFoundException("Measurement not found"));
      return super.hasPermission(authentication, projectId, PROJECT_OBJECT_TYPE, permission);
    }
    return super.hasPermission(authentication, targetId, targetType, permission);
  }

  @Override
  public boolean hasPermission(Authentication authentication, Object targetDomainObject,
      Object permission) {
    if (isEmptyOptional(targetDomainObject)) {
      return true;
    }
    return super.hasPermission(authentication, targetDomainObject, permission);
  }
}
