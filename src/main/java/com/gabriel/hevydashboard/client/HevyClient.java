package com.gabriel.hevydashboard.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Pages;
import com.gabriel.hevydashboard.model.Routine;
import com.gabriel.hevydashboard.model.Workout;
import com.gabriel.hevydashboard.model.WorkoutEvent;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Cliente da API do Hevy (somente leitura).
 * A chave vai no header "api-key" e nunca é escrita em log nem em mensagem de erro.
 */
public class HevyClient implements HevySource {

    public static final String DEFAULT_BASE_URL = "https://api.hevyapp.com/v1";

    // Limites de itens por página definidos pela API do Hevy.
    private static final int WORKOUTS_PAGE_SIZE = 10;
    private static final int EVENTS_PAGE_SIZE = 10;
    private static final int ROUTINES_PAGE_SIZE = 10;
    private static final int TEMPLATES_PAGE_SIZE = 100;

    private static final int MAX_ATTEMPTS = 3;

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = HevyJson.mapper();

    public HevyClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public HevyClient(String apiKey, String baseUrl, HttpClient http) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("A chave de API do Hevy não pode ser vazia.");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
    }

    /** Cria o cliente lendo a chave da variável de ambiente HEVY_API_KEY. */
    public static HevyClient fromEnvironment() {
        String key = System.getenv("HEVY_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Variável de ambiente HEVY_API_KEY não encontrada. "
                            + "Defina a variável e reinicie o terminal ou o VS Code.");
        }
        return new HevyClient(key);
    }

    public int getWorkoutCount() {
        JsonNode node = get("/workouts/count", Map.of(), JsonNode.class);
        return node.path("workout_count").asInt();
    }

    public List<Workout> getAllWorkouts() {
        return fetchAll("/workouts", Map.of(), WORKOUTS_PAGE_SIZE,
                Pages.WorkoutsPage.class, Pages.WorkoutsPage::pageCount, Pages.WorkoutsPage::workouts);
    }

    /**
     * Treinos atualizados ou apagados desde {@code since}.
     * Para importar todo o histórico, use {@link Instant#EPOCH}.
     */
    @Override
    public List<WorkoutEvent> getWorkoutEventsSince(Instant since) {
        return fetchAll("/workouts/events", Map.of("since", since.toString()), EVENTS_PAGE_SIZE,
                Pages.WorkoutEventsPage.class, Pages.WorkoutEventsPage::pageCount, Pages.WorkoutEventsPage::events);
    }

    @Override
    public List<Routine> getAllRoutines() {
        return fetchAll("/routines", Map.of(), ROUTINES_PAGE_SIZE,
                Pages.RoutinesPage.class, Pages.RoutinesPage::pageCount, Pages.RoutinesPage::routines);
    }

    @Override
    public List<ExerciseTemplate> getAllExerciseTemplates() {
        return fetchAll("/exercise_templates", Map.of(), TEMPLATES_PAGE_SIZE,
                Pages.ExerciseTemplatesPage.class, Pages.ExerciseTemplatesPage::pageCount,
                Pages.ExerciseTemplatesPage::exerciseTemplates);
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private <P, T> List<T> fetchAll(String path, Map<String, String> extraParams, int pageSize,
                                    Class<P> pageType, ToIntFunction<P> pageCountOf,
                                    Function<P, List<T>> itemsOf) {
        List<T> all = new ArrayList<>();
        int current = 1;
        int total;
        do {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("page", String.valueOf(current));
            params.put("pageSize", String.valueOf(pageSize));
            params.putAll(extraParams);

            P page = get(path, params, pageType);
            List<T> items = itemsOf.apply(page);
            if (items != null) {
                all.addAll(items);
            }
            total = pageCountOf.applyAsInt(page);
            current++;
        } while (current <= total);
        return all;
    }

    private <T> T get(String path, Map<String, String> params, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path, params))
                .timeout(Duration.ofSeconds(30))
                .header("api-key", apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new HevyApiException("Falha de rede ao chamar " + path, -1, e);
                }
                pause(attempt);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HevyApiException("Chamada interrompida: " + path, -1, e);
            }

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                try {
                    return mapper.readValue(response.body(), type);
                } catch (JsonProcessingException e) {
                    throw new HevyApiException("Resposta inesperada da API em " + path, status, e);
                }
            }

            boolean temporario = status == 429 || status >= 500;
            if (temporario && attempt < MAX_ATTEMPTS) {
                pause(attempt);
                continue;
            }
            throw new HevyApiException(
                    "A API do Hevy respondeu " + status + " em " + path + ": " + resumo(response.body()),
                    status);
        }
    }

    private URI buildUri(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(baseUrl).append(path);
        if (!params.isEmpty()) {
            sb.append('?').append(params.entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&")));
        }
        return URI.create(sb.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String resumo(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    /** Espera antes de tentar de novo: 1 s, 2 s, ... */
    private static void pause(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HevyApiException("Espera interrompida", -1, e);
        }
    }
}
