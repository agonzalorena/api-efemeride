package org.example.efemerideapi.service;

import org.example.efemerideapi.entity.Efemeride;
import org.example.efemerideapi.entity.EfemerideDTO;
import org.example.efemerideapi.exception.BadRequestException;
import org.example.efemerideapi.repository.EfemerideRepo;
import org.example.efemerideapi.utils.GeminiWebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;


@Service
public class EfemerideService {
    @Autowired
    private EfemerideRepo efemerideRepo;
    @Autowired
    private GeminiWebClient geminiWebClient;

    public EfemerideDTO getLast() {
        EfemerideDTO e = efemerideRepo.getLast();
        try {
            //Si la ultima efemeride guardada no coincide con la fecha actual
            if (e==null || !e.getDate().equals(LocalDate.now())) {
                System.out.println("d");
                //forzar actualizacion
                String response = geminiWebClient.getGeminiEfemeride();
                Efemeride n = new Efemeride(LocalDate.now(), response);
                efemerideRepo.save(n);
                e = efemerideRepo.getLast();
            }
            return e;
        } catch (RuntimeException ex) {
            throw new BadRequestException("Error al obtener la efemeride.");
        }
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "${timezone}")
    public void save() throws Exception {
        System.out.println("Ejecutando tarea programada a las 00:00...");
        try {
            String event = geminiWebClient.getGeminiEfemeride();
            Efemeride newEfemeride = new Efemeride(LocalDate.now(), event);
            efemerideRepo.save(newEfemeride);
        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }
    }

}
