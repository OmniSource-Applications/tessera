package live.omnisource.tessera.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import live.omnisource.tessera.filestore.FileStoreService;

@Configuration
public class FileStoreBootstrap {
  @Bean
  public ApplicationRunner fileStoreBootstrapper(FileStoreService fileStoreService) {
    return _ -> fileStoreService.init();
  }
}
