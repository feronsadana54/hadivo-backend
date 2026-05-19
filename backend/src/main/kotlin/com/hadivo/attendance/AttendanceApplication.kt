package com.hadivo.attendance

import com.hadivo.attendance.config.AppProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = [AppProperties::class])
class AttendanceApplication

fun main(args: Array<String>) {
    runApplication<AttendanceApplication>(*args)
}
