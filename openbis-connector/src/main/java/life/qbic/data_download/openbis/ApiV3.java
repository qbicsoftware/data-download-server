package life.qbic.data_download.openbis;

import ch.ethz.sis.openbis.generic.asapi.v3.IApplicationServerApi;
import ch.ethz.sis.openbis.generic.dssapi.v3.IDataStoreServerApi;
import ch.systemsx.cisd.common.spring.HttpInvokerUtils;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * A provider for openbis apis v3
 */
public class ApiV3 {

  private static final Duration TIMEOUT = Duration.of(5, ChronoUnit.DAYS);

  private ApiV3() {
    //hide the implicit constructor
  }

  public static IApplicationServerApi applicationServer(String url) {
    return HttpInvokerUtils.createServiceStub(IApplicationServerApi.class, url + IApplicationServerApi.SERVICE_URL, TIMEOUT.toMillis());
  }
  public static IDataStoreServerApi dataStoreServer(String url) {
    return HttpInvokerUtils.createStreamSupportingServiceStub(IDataStoreServerApi.class, url + IDataStoreServerApi.SERVICE_URL, TIMEOUT.toMillis());
  }

}
