package com.hwan.coupon.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwan.coupon.demo.dto.FirstComeLoadTestRunResponse;
import com.hwan.coupon.demo.dto.FirstComeLoadTestRunsResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@Profile("local")
public class LoadTestEvidenceService {

    private static final String RESULT_PREFIX = "result_a_";
    private static final String RESULT_SUFFIX = ".json";
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FirstComeLoadTestRunsResponse getFirstComeRuns() {
        Path scriptsDir = Paths.get("scripts");
        if (!Files.exists(scriptsDir)) {
            return new FirstComeLoadTestRunsResponse(List.of());
        }

        try (Stream<Path> stream = Files.list(scriptsDir)) {
            List<FirstComeLoadTestRunResponse> runs = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.startsWith(RESULT_PREFIX) && fileName.endsWith(RESULT_SUFFIX);
                    })
                    .map(this::readRunSafely)
                    .filter(run -> run != null)
                    .sorted(Comparator.comparing(FirstComeLoadTestRunResponse::lastModifiedAt).reversed())
                    .limit(8)
                    .toList();
            return new FirstComeLoadTestRunsResponse(runs);
        } catch (IOException e) {
            throw new IllegalStateException("k6 결과 파일을 읽는 중 오류가 발생했습니다", e);
        }
    }

    private FirstComeLoadTestRunResponse readRunSafely(Path path) {
        try {
            return readRun(path);
        } catch (Exception e) {
            return null;
        }
    }

    private FirstComeLoadTestRunResponse readRun(Path path) throws IOException {
        JsonNode root = objectMapper.readTree(path.toFile());
        JsonNode metrics = root.path("metrics");
        JsonNode durationValues = metrics.path("http_req_duration").path("values");
        JsonNode checks = root.path("root_group").path("checks");
        FileTime lastModifiedTime = Files.getLastModifiedTime(path);

        int successCount = 0;
        int duplicateCount = 0;
        int exhaustedCount = 0;

        if (checks.isArray()) {
            for (JsonNode check : checks) {
                String name = check.path("name").asText("");
                int passes = check.path("passes").asInt(0);
                String normalized = name.toLowerCase(Locale.ROOT);
                if (normalized.contains("200") || normalized.contains("202") || normalized.contains("성공") || normalized.contains("접수")) {
                    successCount = Math.max(successCount, passes);
                } else if (normalized.contains("409") || normalized.contains("중복")) {
                    duplicateCount = Math.max(duplicateCount, passes);
                } else if (normalized.contains("410") || normalized.contains("소진")) {
                    exhaustedCount = Math.max(exhaustedCount, passes);
                }
            }
        }

        int httpRequests = metrics.path("http_reqs").path("values").path("count").asInt(0);
        int vus = metrics.path("vus_max").path("values").path("value").asInt(0);
        double failedRate = metrics.path("http_req_failed").path("values").path("rate").asDouble(0.0);
        int otherCount = Math.max(0, httpRequests / 2 - successCount - duplicateCount - exhaustedCount);

        return new FirstComeLoadTestRunResponse(
                path.getFileName().toString(),
                toLabel(path.getFileName().toString()),
                LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZONE_ID),
                vus,
                httpRequests,
                round(durationValues.path("avg").asDouble(0.0)),
                round(durationValues.path("p(90)").asDouble(0.0)),
                round(durationValues.path("p(95)").asDouble(0.0)),
                successCount,
                duplicateCount,
                exhaustedCount,
                otherCount,
                round(failedRate * 100),
                Math.round(root.path("state").path("testRunDurationMs").asDouble(0.0))
        );
    }

    private String toLabel(String fileName) {
        return fileName
                .replace("result_a_", "")
                .replace(".json", "")
                .replace('_', ' ');
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}