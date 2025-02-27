package back.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PageParsingService {

  public Document parsePage(String url, String moodleSession) throws Exception {
    Map<String, String> cookies = Map.of("MoodleSession", moodleSession);

    Connection connection = Jsoup.connect(url)
        .cookies(cookies)
        .timeout(10 * 1000); // таймаут в 10 секунд

    return connection.get();
  }
}
