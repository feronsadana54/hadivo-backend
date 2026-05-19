package com.hadivo.attendance.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI {
        val bearer = SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")

        return OpenAPI()
            .info(
                Info()
                    .title("Hadivo Attendance API")
                    .version("v1")
                    .description("Backend API for the Hadivo multi-tenant attendance system.")
            )
            .components(Components().addSecuritySchemes("bearerAuth", bearer))
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
    }
}
