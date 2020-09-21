package eu.europeana.fulltext.api;

import eu.europeana.api.commons.logs.LoggableDispatcherServlet;
import eu.europeana.fulltext.api.web.SocksProxyConfigInjector;
import org.apache.logging.log4j.LogManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Main application and configuration.
 *
 * @author Lúthien
 * Created on 27-02-2018
 */
@SpringBootApplication(scanBasePackages = {"eu.europeana.fulltext.api", "eu.europeana.fulltext.repository"})
@PropertySource(value = "classpath:build.properties")
public class FTApplication extends SpringBootServletInitializer {

    public static final int THOUSAND = 1000;

    /**
     * Setup CORS for all requests
     *
     * @return
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebConfig();
    }

    @Bean
    public ServletRegistrationBean dispatcherRegistration() {
        return new ServletRegistrationBean(dispatcherServlet());
    }

    @Bean(name = DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)
    public DispatcherServlet dispatcherServlet() {
        return new LoggableDispatcherServlet();
    }


    /**
     * This method is called when starting as a Spring-Boot application (run this class from the IDE)
     *
     * @param args
     */
    @SuppressWarnings("squid:S2095")
    // to avoid sonarqube false positive (see https://stackoverflow.com/a/37073154/741249)
    public static void main(String[] args) {
        LogManager.getLogger(FTApplication.class)
                  .info("CF_INSTANCE_INDEX  = {}, CF_INSTANCE_GUID = {}, CF_INSTANCE_IP  = {}",
                        System.getenv("CF_INSTANCE_INDEX"),
                        System.getenv("CF_INSTANCE_GUID"),
                        System.getenv("CF_INSTANCE_IP"));
        try {
            injectSocksProxySettings();
            SpringApplication.run(FTApplication.class, args);
        } catch (IOException e) {
            LogManager.getLogger(FTApplication.class).fatal("Error reading properties file", e);
            System.exit(-1);
        }
    }

    /**
     * This method is called when starting a 'traditional' war deployment (e.g. in Docker of Cloud Foundry)
     *
     * @param servletContext
     * @throws ServletException
     */
    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        LogManager.getLogger(FTApplication.class)
                  .info("CF_INSTANCE_INDEX  = {}, CF_INSTANCE_GUID = {}, CF_INSTANCE_IP  = {}",
                        System.getenv("CF_INSTANCE_INDEX"),
                        System.getenv("CF_INSTANCE_GUID"),
                        System.getenv("CF_INSTANCE_IP"));
        try {
            injectSocksProxySettings();
            super.onStartup(servletContext);
        } catch (IOException e) {
            throw new ServletException("Error reading properties", e);
        }
    }

    /**
     * Socks proxy settings have to be loaded before anything else, so we check the property files for its settings
     *
     * @throws IOException if properties file cannot be read
     */
    private static void injectSocksProxySettings() throws IOException {
        SocksProxyConfigInjector socksConfig = new SocksProxyConfigInjector("fulltext.properties");
        try {
            socksConfig.addProperties("fulltext.user.properties");
        } catch (IOException e) {
            // user.properties may not be available so only show warning
            LogManager.getLogger(FTApplication.class).warn("Cannot read fulltext.user.properties file", e);
        }
        socksConfig.inject();
    }

    @Configuration
    static class WebConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**").allowedOrigins("*").maxAge(THOUSAND);
        }
    }

}
