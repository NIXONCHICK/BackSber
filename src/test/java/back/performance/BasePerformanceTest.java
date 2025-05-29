package back.performance;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("performance-test")
@Testcontainers
public abstract class BasePerformanceTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15.3-alpine")
            .withDatabaseName("back_performance_test_db")
            .withUsername("perf_user")
            .withPassword("perf_pass")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    protected String baseUrl;

    @BeforeEach
    void setUpPerformanceTest() {
        baseUrl = "http://localhost:" + port + "/api";
    }


    public static class PerformanceMetrics {
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxResponseTime = new AtomicLong(0);
        private final LocalDateTime startTime;
        private LocalDateTime endTime;

        public PerformanceMetrics() {
            this.startTime = LocalDateTime.now();
        }

        public void recordSuccess(long responseTime) {
            successCount.incrementAndGet();
            recordResponseTime(responseTime);
        }

        public void recordError(long responseTime) {
            errorCount.incrementAndGet();
            recordResponseTime(responseTime);
        }

        private void recordResponseTime(long responseTime) {
            totalResponseTime.addAndGet(responseTime);
            minResponseTime.updateAndGet(current -> Math.min(current, responseTime));
            maxResponseTime.updateAndGet(current -> Math.max(current, responseTime));
        }

        public void finish() {
            this.endTime = LocalDateTime.now();
        }

        public int getTotalRequests() {
            return successCount.get() + errorCount.get();
        }

        public int getSuccessCount() {
            return successCount.get();
        }

        public int getErrorCount() {
            return errorCount.get();
        }

        public double getSuccessRate() {
            int total = getTotalRequests();
            return total > 0 ? (double) successCount.get() / total * 100.0 : 0.0;
        }

        public double getAverageResponseTime() {
            int total = getTotalRequests();
            return total > 0 ? (double) totalResponseTime.get() / total : 0.0;
        }

        public long getMinResponseTime() {
            return minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get();
        }

        public long getMaxResponseTime() {
            return maxResponseTime.get();
        }

        public double getThroughput() {
            if (endTime == null) {
                finish();
            }
            long durationSeconds = Duration.between(startTime, endTime).toSeconds();
            return durationSeconds > 0 ? (double) getTotalRequests() / durationSeconds : 0.0;
        }

        public void printReport(String testName) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("📊 ОТЧЕТ ПО НАГРУЗОЧНОМУ ТЕСТИРОВАНИЮ: " + testName);
            System.out.println("=".repeat(60));
            System.out.println("📈 Общая статистика:");
            System.out.printf("   • Всего запросов: %d%n", getTotalRequests());
            System.out.printf("   • Успешных: %d (%.2f%%)%n", getSuccessCount(), getSuccessRate());
            System.out.printf("   • Ошибок: %d (%.2f%%)%n", getErrorCount(), 100.0 - getSuccessRate());
            System.out.println("\n⏱️ Время отклика:");
            System.out.printf("   • Среднее: %.2f мс%n", getAverageResponseTime());
            System.out.printf("   • Минимальное: %d мс%n", getMinResponseTime());
            System.out.printf("   • Максимальное: %d мс%n", getMaxResponseTime());
            System.out.println("\n🚀 Производительность:");
            System.out.printf("   • Пропускная способность: %.2f запросов/сек%n", getThroughput());
            System.out.printf("   • Длительность теста: %d сек%n", 
                             Duration.between(startTime, endTime != null ? endTime : LocalDateTime.now()).toSeconds());
            System.out.println("=".repeat(60));
        }
    }


    protected PerformanceMetrics executeLoadTest(String testName, int threadCount, int requestsPerThread, 
                                                Runnable testAction) throws InterruptedException {
        System.out.println("\n🚀 Запуск нагрузочного теста: " + testName);
        System.out.printf("   Потоков: %d, Запросов на поток: %d%n", threadCount, requestsPerThread);
        
        PerformanceMetrics metrics = new PerformanceMetrics();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * requestsPerThread);

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            long startTime = System.currentTimeMillis();
                            testAction.run();
                            long responseTime = System.currentTimeMillis() - startTime;
                            metrics.recordSuccess(responseTime);
                        } catch (Exception e) {
                            long responseTime = System.currentTimeMillis() - System.currentTimeMillis();
                            metrics.recordError(responseTime);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }

            boolean completed = latch.await(5, TimeUnit.MINUTES);
            if (!completed) {
                System.err.println("⚠️ Тест не завершился в отведенное время!");
            }

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }

        metrics.finish();
        metrics.printReport(testName);
        return metrics;
    }
} 