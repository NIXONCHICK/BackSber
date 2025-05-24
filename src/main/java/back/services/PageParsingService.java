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
        .followRedirects(true)
        .maxBodySize(0)
        .timeout(10 * 1000);

    return connection.get();
  }
}
