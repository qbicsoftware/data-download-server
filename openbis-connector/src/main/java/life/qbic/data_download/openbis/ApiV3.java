package life.qbic.data_download.openbis;

import ch.ethz.sis.openbis.generic.asapi.v3.IApplicationServerApi;
import ch.ethz.sis.openbis.generic.dssapi.v3.IDataStoreServerApi;
import ch.systemsx.cisd.common.spring.HttpInvokerUtils;

/**
 * A provider for openbis apis v3
 */
public class ApiV3 {

  private ApiV3() {
    //hide the implicit constructor
  }
  public static IApplicationServerApi applicationServer(String url) {
    return HttpInvokerUtils.createServiceStub(IApplicationServerApi.class, url + IApplicationServerApi.SERVICE_URL, 100_000L);
  }
  public static IDataStoreServerApi dataStoreServer(String url) {
    return HttpInvokerUtils.createStreamSupportingServiceStub(IDataStoreServerApi.class, url + IDataStoreServerApi.SERVICE_URL, 100_000L);
  }

}
