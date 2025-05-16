package back.util;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.NoSuchElementException;

import java.time.Duration;
import java.util.Set;

public class SeleniumUtil {

  public static SfedLoginResult loginAndGetMoodleSession(String email, String password) {

    ChromeOptions options = new ChromeOptions();
    options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage", "--start-maximized");
    WebDriver driver = null;
    String moodleSession = null;

    try {
      driver = new ChromeDriver(options);
      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));

      driver.get("https://lms.sfedu.ru/login/index.php");

      WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(
          By.cssSelector("a.btn.login-identityprovider-btn.btn-block")));
      loginButton.click();

      WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("i0116")));
      emailField.sendKeys(email);

      WebElement nextButtonAfterEmail = wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9")));
      nextButtonAfterEmail.click();

      String errorEmailXPath = "//div[contains(@id, 'usernameError') or contains(@id, 'errorText') or contains(@class, 'error') or contains(@class, 'alert') or contains(., 'не существует') or contains(., 'Введите действительный') or contains(., 'Проверьте адрес')]";
      
      wait.until(ExpectedConditions.or(
          ExpectedConditions.visibilityOfElementLocated(By.id("i0118")),
          ExpectedConditions.visibilityOfElementLocated(By.xpath(errorEmailXPath))
      ));
      
      WebElement passwordField = null;

      try {
          passwordField = driver.findElement(By.id("i0118"));
      } catch (NoSuchElementException e) { }

      if (passwordField != null && passwordField.isDisplayed()) {
          passwordField.sendKeys(password);

          WebElement signInButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9"))); // ID тот же для кнопки "Войти"
          signInButton.click();

          boolean successfulRedirect = false;
          
          String staySignedInTextXPath = "//div[contains(text(),'Не выходить из системы?')] | //div[contains(text(),'Stay signed in?')] | //div[contains(text(),'Сделайте это, чтобы сократить число запросов на вход.')]";
          String yesButtonSelector = "idSIButton9";
          String errorPasswordXPath = "//div[contains(@id, 'passwordError') or contains(@id, 'errorText') or contains(@class, 'error') or contains(@class, 'alert') or contains(., 'неправильный пароль') or contains(., 'повторите попытку') or contains(., 'Пароль нешен') or contains(., 'incorrect password') or contains(text(), 'Пароль введен неправильно')]";

          try {
              WebDriverWait quickStaySignedInWait = new WebDriverWait(driver, Duration.ofSeconds(5)); // Короткий таймаут 5 секунд
              System.out.println("Quickly checking for 'Stay signed in?' prompt (5s timeout)...");

              quickStaySignedInWait.until(ExpectedConditions.and(
                  ExpectedConditions.visibilityOfElementLocated(By.xpath(staySignedInTextXPath)),
                  ExpectedConditions.elementToBeClickable(By.id(yesButtonSelector))
              ));

              System.out.println("'Stay signed in?' prompt found and 'Yes' button is clickable (quick check). Clicking 'Yes'.");
              WebElement yesButtonElement = driver.findElement(By.id(yesButtonSelector));
              yesButtonElement.click();

              System.out.println("Waiting for redirection to lms.sfedu.ru after quick 'Yes' click (main wait timeout)...");
              wait.until(ExpectedConditions.and(
                  ExpectedConditions.urlContains("lms.sfedu.ru"),
                  ExpectedConditions.not(ExpectedConditions.urlContains("login"))
              ));
              System.out.println("Successfully redirected to lms.sfedu.ru after quick 'Stay signed in?' prompt.");
              successfulRedirect = true;

          } catch (org.openqa.selenium.TimeoutException e) {
              System.out.println("'Stay signed in?' prompt not found/actionable within quick 5s check. Proceeding to check for password error or direct LMS redirect.");
          } catch (Exception e) {
              System.out.println("Exception during quick check for 'Stay signed in?': " + e.getMessage() + ". Proceeding with other checks.");
          }

          if (!successfulRedirect) {
              try {
                  WebDriverWait passwordErrorWait = new WebDriverWait(driver, Duration.ofSeconds(7)); 
                  System.out.println("Checking for password error (7s timeout)...");
                  passwordErrorWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(errorPasswordXPath)));
                  
                  System.err.println("Microsoft login error (password stage) detected after quick 'Stay signed in?' check failed.");
                  return new SfedLoginResult(null, "INVALID_SFEDU_PASSWORD", "Неверный пароль SFEDU.");
              } catch (org.openqa.selenium.TimeoutException e) {
                  System.out.println("No password error detected. Checking for direct LMS redirect.");
              } catch (Exception e) {
                   System.err.println("Exception while checking for password error: " + e.getMessage());
              }

              try {
                  System.out.println("Waiting for direct LMS redirect (main wait timeout)...");
                  wait.until(ExpectedConditions.and(
                      ExpectedConditions.urlContains("lms.sfedu.ru"),
                      ExpectedConditions.not(ExpectedConditions.urlContains("login"))
                  ));
                  System.out.println("Successfully redirected to lms.sfedu.ru (direct redirect).");
                  successfulRedirect = true;
              } catch (org.openqa.selenium.TimeoutException e) {
                  System.err.println("Timeout waiting for LMS redirect. Neither 'Stay signed in?' (quick), nor password error, nor direct redirect occurred.");
                  try {
                      if (driver.findElement(By.xpath(errorPasswordXPath)).isDisplayed()) {
                           System.err.println("Found password error element on page after final timeout for LMS redirect.");
                           return new SfedLoginResult(null, "INVALID_SFEDU_PASSWORD", "Неверный пароль SFEDU (обнаружен после таймаута редиректа).");
                      }
                  } catch (NoSuchElementException ignored) {}
                  return new SfedLoginResult(null, "SFEDU_LOGIN_TIMEOUT", "Тайм-аут при входе в SFEDU (не удалось определить исход после этапа пароля).");
              } catch (Exception e) {
                   System.err.println("Exception while waiting for direct LMS redirect: " + e.getMessage());
                   return new SfedLoginResult(null, "SFEDU_LOGIN_UNKNOWN_ERROR", "Ошибка при ожидании редиректа на LMS.");
              }
          }

          if (!successfulRedirect) {
               System.err.println("Login process concluded without a confirmed successful redirect. This state is unexpected. Current URL: " + driver.getCurrentUrl());
               return new SfedLoginResult(null, "SFEDU_LOGIN_CRITICAL_FAILURE", "Критическая ошибка в процессе логина SFEDU (не удалось подтвердить успешный редирект).");
          }
          
          System.out.println("Successfully authenticated with Microsoft and redirected to SFEDU LMS.");

          Set<Cookie> cookies = driver.manage().getCookies();
          for (Cookie cookie : cookies) {
            if ("MoodleSession".equals(cookie.getName())) {
              moodleSession = cookie.getValue();
              System.out.println("MoodleSession cookie found: " + moodleSession);
              break;
            }
          }
          if (moodleSession == null) {
               System.err.println("MoodleSession cookie NOT found after login.");
               return new SfedLoginResult(null, "MOODLE_SESSION_NOT_FOUND", "Сессия Moodle не найдена после успешного входа в SFEDU.");
          }
          return new SfedLoginResult(moodleSession, "SUCCESS", null);

      } else {
          System.err.println("Microsoft login error (email stage) detected. Password field was not found/displayed.");
          return new SfedLoginResult(null, "INVALID_SFEDU_EMAIL", "Неверный email SFEDU.");
      }
    } catch (org.openqa.selenium.TimeoutException e) {
        System.err.println("Selenium operation timed out.");
        e.printStackTrace();
        return new SfedLoginResult(null, "SFEDU_LOGIN_TIMEOUT", "Тайм-аут при входе в SFEDU.");
    } catch (Exception e) {
      System.err.println("An overall exception occurred during Selenium operations:");
      e.printStackTrace();
      return new SfedLoginResult(null, "SFEDU_LOGIN_UNKNOWN_ERROR", "Ошибка при входе в SFEDU.");
    } finally {
      if (driver != null) {
        System.out.println("Quitting WebDriver.");
        driver.quit();
      }
    }
  }
}
