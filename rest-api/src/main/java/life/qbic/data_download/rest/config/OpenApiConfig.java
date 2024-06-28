package life.qbic.data_download.rest.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "QBiC Data Manager Download API", version = "v1"),
    security = @SecurityRequirement(name = "personal_access_token")) //globally set this
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI(
      @Value("${server.download.token-name}") String tokenName
  ) {
    io.swagger.v3.oas.models.security.SecurityScheme securityScheme = new io.swagger.v3.oas.models.security.SecurityScheme()
        .type(Type.APIKEY)
        .in(In.HEADER)
        .description(
            "A personal access token (PAT) obtained through by the data manager. Please prefix your token with '"
                + tokenName + " " + "' e.g. '" + tokenName + "  abcdefg1234'.")
        .name("Authorization");
    var securityComponent = new Components()
        .addSecuritySchemes("personal_access_token", securityScheme);

    return new OpenAPI()
        .components(securityComponent);
  }

}
