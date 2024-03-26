package life.qbic.data_download.rest.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "My API", version = "v1"),
    security = @SecurityRequirement(name = "personal_access_token")) //globally set this
@SecurityScheme(
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    name = "personal_access_token",
    paramName = "Authorization"
)
public class OpenApiConfig {

}
