package life.qbic.data_download.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The download server
 */
@SpringBootApplication
public class DataDownloadRestServer {

  public static void main(String[] args) {
    SpringApplication.run(DataDownloadRestServer.class, args);
  }
}
