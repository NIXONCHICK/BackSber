package back.util;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Set;

public class SeleniumUtil {

  public static String loginAndGetMoodleSession(String email, String password) {
    System.setProperty("webdriver.chrome.driver", "C://drivers//chromedriver.exe");

    ChromeOptions options = new ChromeOptions();
    options.addArguments("--disable-gpu", "--no-sandbox", "--start-maximized");
    WebDriver driver = new ChromeDriver(options);
    String moodleSession = null;

    try {
      driver.get("https://lms.sfedu.ru/login/index.php");

      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));

      WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(
          By.cssSelector("a.btn.login-identityprovider-btn.btn-block")));
      loginButton.click();

      WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("i0116")));
      emailField.sendKeys(email);

      WebElement nextButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9")));
      nextButton.click();

      WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("i0118")));
      passwordField.sendKeys(password);

      WebElement signInButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9")));
      signInButton.click();

      WebElement yesButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9")));
      yesButton.click();

      Thread.sleep(5000);

      Set<Cookie> cookies = driver.manage().getCookies();
      for (Cookie cookie : cookies) {
        if ("MoodleSession".equals(cookie.getName())) {
          moodleSession = cookie.getValue();
          break;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      driver.quit();
    }

    return moodleSession;
  }
}
