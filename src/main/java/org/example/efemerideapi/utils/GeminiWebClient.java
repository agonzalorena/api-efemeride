package org.example.efemerideapi.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class GeminiWebClient {
    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    private final WebClient.Builder webClientBuilder;

    public GeminiWebClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public String getGeminiEfemeride() {
        LocalDate today = LocalDate.now();
        int day = today.getDayOfMonth();
        int month = today.getMonthValue();
        Mono<String> response = consultGemini(day + " del mes " + month);
        return Objects.requireNonNull(response.block()).trim();
    }

    public Mono<String> consultGemini(String date) {
        String prompt = "Dame la efeméride más reciente o la más relevante del " + date + ". Responde " +
                "únicamente con el texto de la efeméride en este formato: [día de mes]: [efeméride],[breve explicacion]. " +
                "No agregues introducciones, saludos ni explicaciones, solo la efeméride, la breve explicacion " +
                "debe tener como maximo 500 caracteres.";


        return webClientBuilder.baseUrl("https://generativelanguage.googleapis.com")
                .build()
                .post()
                .uri("/v1beta/models/gemini-flash-latest:generateContent?key=" + API_KEY) // Cambiado a 1.5-flash como sugerimos
                .bodyValue("{ \"contents\": [{ \"role\": \"user\", \"parts\": [{ \"text\": \"" + prompt + "\" }]}]}")
                .retrieve()

                // 1. INTERCEPTAR ERRORES (4xx, 5xx) para ver el body
                .onStatus(httpStatus -> httpStatus.isError(), clientResponse -> {
                    return clientResponse.bodyToMono(String.class) // Leemos el cuerpo del error
                            .flatMap(errorBody -> {
                                System.err.println("ESTADO HTTP: " + clientResponse.statusCode());
                                System.err.println("CUERPO DEL ERROR GEMINI: " + errorBody);
                                return Mono.error(new RuntimeException("Error en API Gemini: " + errorBody));
                            });
                })

                .bodyToMono(JsonNode.class)

                // 2. LOGUEAR RESPUESTA EXITOSA (Para ver qué llega cuando funciona)
                .doOnNext(response -> System.out.println("Respuesta Raw de Gemini: " + response.toString()))

                .map(response -> {
                    // Tu lógica de extracción
                    JsonNode candidates = response.get("candidates");
                    if (candidates == null || candidates.isEmpty()) {
                        throw new RuntimeException("Gemini respondió OK pero sin candidatos.");
                    }
                    JsonNode content = candidates.get(0).get("content");
                    JsonNode parts = content.get("parts");
                    String text = parts.get(0).get("text").asText();
                    return cleanText(text);
                })

                // 3. LOGUEAR ERRORES DE RED O PARSEO
                .doOnError(e -> System.err.println("Error durante el flujo: " + e.getMessage()));
    }

    public String cleanText(String text) {
        //chequear que no empiece con simbolos
        while (text.startsWith("#") || text.startsWith("*") || text.startsWith("-")) {
            text = text.substring(1);
        }
        return text.replace("**", "");
    }
}


//Esto era la vieja forma de hacerlo, no permite asincronia.
/*public class GeminiRestTemplate {
    private static final String API_KEY= "...";
    private static final String API_URL= "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY;
    private final HttpClient client;

    public GeminiRestTemplate(){
        this.client = HttpClient.newHttpClient();
    }

    public String consultGemini(String date) throws Exception{
        String prompt= "Dame una unica efemeride, de forma breve(10 renglones maximo), del dia " + date;
        String jsonBody= "{ \"contents\": [{ \"role\": \"user\", \"parts\": [{ \"text\": \"" + prompt + "\" }]}]}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!= 200){
            throw  new Exception("Error en la llamada de gemini");
        }
        return response.body();
    }*/



