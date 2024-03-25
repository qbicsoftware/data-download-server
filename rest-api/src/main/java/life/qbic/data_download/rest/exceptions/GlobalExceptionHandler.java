package life.qbic.data_download.rest.exceptions;

import static org.slf4j.LoggerFactory.getLogger;

import life.qbic.data_download.rest.exceptions.ErrorMessageTranslationService.UserFriendlyErrorMessage;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The global exception handler. This exception handler takes effect after authentication and
 * authorization of a user. It catches all exceptions thrown in a controller.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  private final ErrorMessageTranslationService errorMessageTranslationService;

  private static final Logger log = getLogger(GlobalExceptionHandler.class);

  public GlobalExceptionHandler(
      @Autowired ErrorMessageTranslationService errorMessageTranslationService) {
    this.errorMessageTranslationService = errorMessageTranslationService;
  }


  @ExceptionHandler(value = GlobalException.class)
  public ResponseEntity<String> globalException(GlobalException globalException) {
    log.error(globalException.getMessage(), globalException);
    UserFriendlyErrorMessage errorMessage = errorMessageTranslationService.translate(
        globalException);
    HttpStatusCode status = switch (globalException.errorCode()) {
      case GENERAL -> HttpStatus.INTERNAL_SERVER_ERROR;
      case MEASUREMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
    };
    return ResponseEntity
        .status(status)
        .contentType(MediaType.TEXT_PLAIN)
        .body("%s\t%s".formatted(errorMessage.title(), errorMessage.message()));
  }

  @ExceptionHandler(value = Exception.class)
  public ResponseEntity<String> unknownException(Exception e) {
    log.error(e.getMessage(), e);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("Something went wrong. Please try again later.");
  }


}
