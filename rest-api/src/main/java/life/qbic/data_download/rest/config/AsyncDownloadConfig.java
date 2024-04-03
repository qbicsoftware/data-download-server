package life.qbic.data_download.rest.config;

import java.time.Duration;
import java.util.concurrent.Executor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
@EnableAsync
public class AsyncDownloadConfig implements AsyncConfigurer {

  @Override
  @Bean("taskExecutor")
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
    threadPoolTaskExecutor.setCorePoolSize(2);
    threadPoolTaskExecutor.setMaxPoolSize(2);
    threadPoolTaskExecutor.setQueueCapacity(200);
    threadPoolTaskExecutor.setThreadNamePrefix("download - ");
    threadPoolTaskExecutor.initialize();
    return threadPoolTaskExecutor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new SimpleAsyncUncaughtExceptionHandler();
  }

  @Bean
  public WebMvcConfigurer webMvcConfigurer(
      @Qualifier("taskExecutor") final AsyncTaskExecutor taskExecutor,
      @Value("${spring.mvc.async.request-timeout}") Duration timeout) {
    return new WebMvcConfigurer() {
      @Override
      public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(timeout.toMillis()).setTaskExecutor(taskExecutor);

      }
    };
  }

}
