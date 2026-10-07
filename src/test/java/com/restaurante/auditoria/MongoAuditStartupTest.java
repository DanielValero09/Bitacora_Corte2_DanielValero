package com.restaurante.auditoria;

import com.restaurante.mapper.EventoRestauranteMapperImpl;
import com.restaurante.repository.EventoRestauranteRepository;
import com.restaurante.service.auditoria.EventoAuditoriaMongoListener;
import com.restaurante.service.impl.EventoAuditoriaServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class MongoAuditStartupTest {
    @Test
    void infraestructuraMongoArrancaSinServidorNiOperacionesDeAuditoria() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class,
                        MongoDataAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class))
                .withUserConfiguration(AuditConfiguration.class)
                .withPropertyValues("spring.data.mongodb.uri=mongodb://127.0.0.1:1/american_bites"
                        + "?serverSelectionTimeoutMS=100&connectTimeoutMS=100")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EventoRestauranteRepository.class);
                    assertThat(context).hasSingleBean(EventoAuditoriaMongoListener.class);
                    assertThat(context).hasSingleBean(EventoAuditoriaServiceImpl.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = EventoRestauranteRepository.class)
    @Import({EventoRestauranteMapperImpl.class, EventoAuditoriaMongoListener.class,
            EventoAuditoriaServiceImpl.class})
    static class AuditConfiguration {
    }
}
