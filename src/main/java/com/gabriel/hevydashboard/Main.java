package com.gabriel.hevydashboard;

import com.gabriel.hevydashboard.client.HevyApiException;
import com.gabriel.hevydashboard.client.HevyClient;
import com.gabriel.hevydashboard.model.Exercise;
import com.gabriel.hevydashboard.model.ExerciseTemplate;
import com.gabriel.hevydashboard.model.Routine;

import java.util.List;

/**
 * Ponto de entrada. Nesta etapa faz só um teste de conexão com o Hevy:
 * confere a chave, lê as rotinas e os exercícios. A sincronização completa vem nas próximas etapas.
 */
public class Main {

    public static void main(String[] args) {
        try {
            HevyClient client = HevyClient.fromEnvironment();

            System.out.println("Treinos salvos no Hevy: " + client.getWorkoutCount());

            List<Routine> routines = client.getAllRoutines();
            System.out.println("Rotinas encontradas: " + routines.size());
            for (Routine routine : routines) {
                int sets = routine.exercises().stream().mapToInt(e -> e.sets().size()).sum();
                System.out.printf("  - %s: %d exercícios, %d séries (com aquecimento)%n",
                        routine.title(), routine.exercises().size(), sets);
            }

            List<ExerciseTemplate> templates = client.getAllExerciseTemplates();
            System.out.println("Exercícios cadastrados (templates): " + templates.size());

            routines.stream()
                    .flatMap(r -> r.exercises().stream())
                    .map(Exercise::title)
                    .distinct()
                    .limit(3)
                    .forEach(t -> System.out.println("  exemplo de exercício em rotina: " + t));

        } catch (HevyApiException | IllegalStateException e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }
}
